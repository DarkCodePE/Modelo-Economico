package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.dto.projections.ParamFilterDTO;
import ms.hispam.budget.util.Shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
@Slf4j(topic = "Colombia")
public class Colombia {
    private static final Integer SALARY_MIN = 1160000;
    private static final Integer SALARY_MIN_PRA = 1424115;
    private static final Integer SALARY_MIN_INC_PLA_DIR = (SALARY_MIN*112)/100;
    private static final Integer SALARY_MIN_PRA_INC_PLA_DIR = (SALARY_MIN_PRA*112)/100;
    static final String TYPEMONTH="yyyyMM";
    private static final String PC938001 = "PC938001";
    private static final String PC938005 = "PC938005";
    private static final String SALARY = "SALARY";
    private static final String TEMPORAL_SALARY = "TEMPORAL_SALARY";
    private static final String SALARY_PRA = "SALARY_PRA";
    private Map<String, Object> createSalaryComponent(PaymentComponentDTO pc938001Component, PaymentComponentDTO pc938005Component, String classEmployee, String period, Integer range, String category, String componentName) {
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent(componentName);
        String salaryType = "BASE";
        double baseSalary = pc938001Component == null ? 0.0 : pc938001Component.getAmount().doubleValue();
        double baseSalaryIntegral = pc938005Component == null ? 0.0 : pc938005Component.getAmount().doubleValue();
        if (classEmployee.equals(category)) {
            if (baseSalary == 0.0) {
                salaryType = "INTEGRAL";
                salaryComponent.setAmount(BigDecimal.valueOf(baseSalaryIntegral));
            } else {
                salaryComponent.setAmount(BigDecimal.valueOf(baseSalary));
            }
        } else {
            salaryComponent.setAmount(BigDecimal.ZERO);
        }
        salaryComponent.setSalaryType(salaryType);
        salaryComponent.setProjections(Shared.generateMonthProjection(period,range,salaryComponent.getAmount()));
        Map<String, Object> result = new HashMap<>();
        result.put("salaryComponent", salaryComponent);
        result.put("salaryType", salaryType);
        return result;
    }
    private Map<String, Object> createSalaryComponent(PaymentComponentDTO pc938001Component, PaymentComponentDTO pc938005Component, String classEmployee, String period, Integer range, List<String> categories, String componentName) {
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent(componentName);
        String salaryType = "BASE";
        // Calcular el valor de THEORETICAL-SALARY a partir de PC960400 y PC960401
        double baseSalary = pc938001Component == null ? 0.0 : pc938001Component.getAmount().doubleValue();
        double baseSalaryIntegral = pc938005Component == null ? 0.0 : pc938005Component.getAmount().doubleValue();
        if (categories.contains(classEmployee)) {
            if (baseSalary == 0.0) {
                salaryType = "INTEGRAL";
                salaryComponent.setAmount(BigDecimal.valueOf(baseSalaryIntegral));
            } else {
                salaryComponent.setAmount(BigDecimal.valueOf(baseSalary));
            }
        } else {
            salaryComponent.setAmount(BigDecimal.ZERO);
        }
        salaryComponent.setSalaryType(salaryType);
        salaryComponent.setProjections(Shared.generateMonthProjection(period,range,salaryComponent.getAmount()));
        Map<String, Object> result = new HashMap<>();
        result.put("salaryComponent", salaryComponent);
        result.put("salaryType", salaryType);
        return result;
    }
    public String findCategory(String currentCategory) {
        Map<String, String> categoryTitleMap = new HashMap<>();
        categoryTitleMap.put("Joven Profesional", "JP");
        categoryTitleMap.put("Joven Talento", "JP");
        categoryTitleMap.put("Profesional Talento", "JP");
        if ("EMP".equals(currentCategory)) {
            String category = categoryTitleMap.get(currentCategory);
            return Objects.requireNonNullElse(category, "P");
        } else {
            return currentCategory;
        }
    }
    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range){
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc938001Component = componentMap.get(PC938001);
        PaymentComponentDTO pc938005Component = componentMap.get(PC938005);
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        String periodSalaryMin = legalSalaryMin == null ? period : legalSalaryMin.getPeriod();
        PaymentComponentDTO paymentComponentDTO = (PaymentComponentDTO)createSalaryComponent(pc938001Component, pc938005Component, category, period, range, "P", SALARY).get("salaryComponent");
        if (paymentComponentDTO != null && paymentComponentDTO.getAmount().doubleValue() != 0.0) {
            String salaryType = paymentComponentDTO.getSalaryType();
            ParametersDTO salaryMinIntegralParam = getParametersById(parameters, 42);
            double salaryMinIntegral = salaryMinIntegralParam == null ? 0.0 : salaryMinIntegralParam.getValue();
            String periodSalaryMinIntegral = salaryMinIntegralParam== null ? period : salaryMinIntegralParam.getPeriod();
            int idxEmp = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()), periodSalaryMin);
            int idxEmpIntegral = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()), periodSalaryMinIntegral);
            if (salaryType.equals("INTEGRAL")) {
                if (idxEmpIntegral != -1) {
                    for (int i = idxEmpIntegral; i < paymentComponentDTO.getProjections().size(); i++) {
                        double amount = i == 0 ? paymentComponentDTO.getProjections().get(i).getAmount().doubleValue() : paymentComponentDTO.getProjections().get(i - 1).getAmount().doubleValue();
                        paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                        //ignore month marcth
                        if (paymentComponentDTO
                                .getProjections()
                                .get(i).getMonth().equalsIgnoreCase(periodSalaryMinIntegral)) {
                            if (amount <= salaryMinIntegral) {
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(salaryMinIntegral));
                            } else {
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                            }
                        }
                    }
                }
            } else {
                if (idxEmp != -1) {
                    for (int i = idxEmp; i < paymentComponentDTO.getProjections().size(); i++) {
                        double amount = i == 0 ? paymentComponentDTO.getProjections().get(i).getAmount().doubleValue() : paymentComponentDTO.getProjections().get(i - 1).getAmount().doubleValue();
                        paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                        //ignore month marcth
                        if (paymentComponentDTO
                                .getProjections()
                                .get(i).getMonth().equalsIgnoreCase(periodSalaryMin)) {
                            if (amount <= legalSalaryMinInternal) {
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(legalSalaryMinInternal));
                            } else {
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                            }
                        }
                    }
                }
            }
        }
        component.add(paymentComponentDTO);
    }
    private ParametersDTO getParametersById(List<ParametersDTO> parameters, int id) {
        return parameters.stream()
                .filter(p -> p.getParameter().getId() == id)
                .findFirst()
                .orElse(null);
    }
    private Map<String, PaymentComponentDTO> createComponentMap(List<PaymentComponentDTO> component) {
        return component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> {
                    existing.getProjections().addAll(replacement.getProjections());
                    return existing;
                }));
    }
    public void temporalSalary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range){
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc938001Component = componentMap.get(PC938001);
        PaymentComponentDTO pc938005Component = componentMap.get(PC938005);
        String category = findCategory(classEmployee);
        PaymentComponentDTO paymentComponentDTO = (PaymentComponentDTO)createSalaryComponent(pc938001Component, pc938005Component, category, period, range, "T", TEMPORAL_SALARY).get("salaryComponent");
        if (paymentComponentDTO != null && paymentComponentDTO.getAmount().doubleValue() != 0.0) {
            ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
            double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
            String salaryType = paymentComponentDTO.getSalaryType();
            ParametersDTO salaryMinIntegralParam = getParametersById(parameters, 42);
            String periodSalaryMin = legalSalaryMin == null ? period : legalSalaryMin.getPeriod();
            if (paymentComponentDTO.getProjections() != null){
                double salaryMinIntegral = salaryMinIntegralParam == null ? 0.0 : salaryMinIntegralParam.getValue();
                String periodSalaryMinIntegral = salaryMinIntegralParam== null ? period : salaryMinIntegralParam.getPeriod();
                int idxEmp = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()), periodSalaryMin);
                int idxEmpIntegral = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()), periodSalaryMinIntegral);
                if (salaryType.equals("INTEGRAL")) {
                    if (idxEmpIntegral != -1) {
                        for (int i = idxEmpIntegral; i < paymentComponentDTO.getProjections().size(); i++) {
                            double amount = i == 0 ? paymentComponentDTO.getProjections().get(i).getAmount().doubleValue() : paymentComponentDTO.getProjections().get(i - 1).getAmount().doubleValue();
                            paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                            //ignore month marcth
                            if (paymentComponentDTO
                                    .getProjections()
                                    .get(i).getMonth().equalsIgnoreCase(periodSalaryMinIntegral)) {
                                if (amount <= salaryMinIntegral) {
                                    paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(salaryMinIntegral));
                                } else {
                                    paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                                }
                            }
                        }
                    }
                } else {
                    if (idxEmp != -1) {
                        for (int i = idxEmp; i < paymentComponentDTO.getProjections().size(); i++) {
                            double amount = i == 0 ? paymentComponentDTO.getProjections().get(i).getAmount().doubleValue() : paymentComponentDTO.getProjections().get(i - 1).getAmount().doubleValue();
                            paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                            if (paymentComponentDTO
                                    .getProjections()
                                    .get(i).getMonth().equalsIgnoreCase(periodSalaryMin)) {
                                if (amount <= legalSalaryMinInternal) {
                                    paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(legalSalaryMinInternal));
                                } else {
                                    paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                                }
                            }
                        }
                    }
                }
            }
        }
        component.add(paymentComponentDTO);
    }
    public void salaryPra(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range){
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc938001Component = componentMap.get(PC938001);
        PaymentComponentDTO pc938005Component = componentMap.get(PC938005);
        //TRAER COMPONENTES DE PAGO PARA CALCULAR EL SALARIO BASE PC938001 - PC938005
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        String category = findCategory(classEmployee);
        PaymentComponentDTO paymentComponentDTO = (PaymentComponentDTO)createSalaryComponent(pc938001Component, pc938005Component, category, period, range, Arrays.asList("PRA", "APR"), SALARY_PRA).get("salaryComponent");
        if (paymentComponentDTO != null && paymentComponentDTO.getAmount().doubleValue() != 0.0) {
            String salaryType = paymentComponentDTO.getSalaryType();
            ParametersDTO salaryMinPraParam = getParametersById(parameters, 26);
            double salaryPra = salaryMinPraParam == null ? 0.0 : salaryMinPraParam.getValue();
            String periodSalaryMin = legalSalaryMin == null ? period : legalSalaryMin.getPeriod();
            String periodSalaryPra = salaryMinPraParam == null ? period : salaryMinPraParam.getPeriod();
            if (paymentComponentDTO.getProjections() != null){
                int idxEmp = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()), periodSalaryMin);
                int idxEmpPra = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()), periodSalaryPra);
                if (salaryType.equals("BASE")) {
                    if (classEmployee.equals("PRA")) {
                        if (idxEmpPra != -1) {
                            for (int i = idxEmp; i < paymentComponentDTO.getProjections().size(); i++) {
                                double amount = i == 0 ? paymentComponentDTO.getProjections().get(i).getAmount().doubleValue() : paymentComponentDTO.getProjections().get(i - 1).getAmount().doubleValue();
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                                //ignore month marcth
                                if (paymentComponentDTO
                                        .getProjections()
                                        .get(i).getMonth().equalsIgnoreCase(periodSalaryPra)) {
                                    if (amount <= salaryPra) {
                                        paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(salaryPra));
                                    } else {
                                        paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                                    }
                                }
                            }
                        }
                    }
                    if (classEmployee.equals("APR") && legalSalaryMin != null) {
                        if (idxEmp != -1) {
                            for (int i = idxEmp; i < paymentComponentDTO.getProjections().size(); i++) {
                                double amount = i == 0 ? paymentComponentDTO.getProjections().get(i).getAmount().doubleValue() : paymentComponentDTO.getProjections().get(i - 1).getAmount().doubleValue();
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                                //ignore month marcth
                                if (paymentComponentDTO
                                        .getProjections()
                                        .get(i).getMonth().equalsIgnoreCase(periodSalaryMin)) {
                                    if (amount <= legalSalaryMinInternal/2) {
                                        paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(legalSalaryMinInternal/2));
                                    } else {
                                        //TODO : PREGUNTAR SI DEBE MANTENER EL SALARIO
                                        paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(legalSalaryMinInternal));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        component.add(paymentComponentDTO);
    }

    public void revisionSalary(List<PaymentComponentDTO> component,List<ParametersDTO> parameters,String period, Integer range){
        // Obtén los componentes necesarios para el cálculo
        List<String> salaryComponents = Arrays.asList("SALARY", "TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> salaryComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));

        // Itera sobre cada componente de salario
        for (String salaryComponentName : salaryComponents) {
            PaymentComponentDTO salaryComponent = componentMap.get(salaryComponentName);
            if (salaryComponent != null) {
                // Realiza el cálculo de revisión de salario para el componente actual
                calculateSalaryRevision(salaryComponent, parameters, period, range, salaryComponentName);
            } else {
                // Lanza una excepción si el componente de salario no se encuentra
                throw new RuntimeException("El componente de salario " + salaryComponentName + " no se encuentra en la lista de componentes.");
            }
        }
    }


    public void calculateSalaryRevision(PaymentComponentDTO paymentComponentDTO,List<ParametersDTO> parameters,String period, Integer range, String salaryComponentName){
        // Asegúrate de que el período y el rango no son nulos antes de usarlos
        if (period == null || range == null) {
            throw new IllegalArgumentException("El período y el rango no pueden ser nulos.");
        }
        double differPercent=0.0;
        ParametersDTO revisionSalaryMin = getParametersById(parameters, 46);
        ParametersDTO revisionSalaryMinEtt = getParametersById(parameters, 45);

       if (paymentComponentDTO.getProjections() != null) {
           // Lógica específica para el componente SALARY
           if (revisionSalaryMin != null) {
               if (Boolean.TRUE.equals(revisionSalaryMin.getIsRetroactive())) {
                   int idxStart;
                   int idxEnd;
                   String[] periodRevisionSalary = revisionSalaryMin.getPeriodRetroactive().split("-");
                   idxStart = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                           .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary[0]);
                   idxEnd = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                           .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary.length == 1 ? periodRevisionSalary[0] : periodRevisionSalary[1]);
                   AtomicReference<Double> salaryFirst = new AtomicReference<>(0.0);
                   AtomicReference<Double> salaryEnd = new AtomicReference<>(0.0);
                   salaryFirst.set(paymentComponentDTO.getProjections().get(idxStart).getAmount().doubleValue());
                   salaryEnd.set(paymentComponentDTO.getProjections().get(idxEnd).getAmount().doubleValue());
                   differPercent = (salaryEnd.get()) / (salaryFirst.get()) - 1;
               }
               double percent = revisionSalaryMin.getValue() / 100;
               int idx = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                       .map(MonthProjection::getMonth).collect(Collectors.toList()), revisionSalaryMin.getPeriod());
               if (idx != -1) {
                   for (int i = idx; i < paymentComponentDTO.getProjections().size(); i++) {
                       double revisionSalaryAmount = 0;
                       double amount = i == 0 ? paymentComponentDTO.getProjections().get(i).getAmount().doubleValue() : paymentComponentDTO.getProjections().get(i - 1).getAmount().doubleValue();
                       paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                       if (paymentComponentDTO.getProjections().get(i).getMonth().equalsIgnoreCase(revisionSalaryMin.getPeriod())) {
                           // R13 * ( 1 +SI(Q13 / P13 - 1 > 0;SI(Q13 / P13 - 1 <= S$8;S$8 - ( Q13 / P13 - 1 );0);S$8 ) ))
                           if (differPercent > 0) {
                               // 9%
                               if (differPercent <= percent) {
                                   //log.info("differPercent si es menor -> {}", differPercent);
                                   differPercent = percent - differPercent;
                               } else {
                                   differPercent = 0;
                               }
                           } else {
                               differPercent = percent;
                           }
                           //log.info("differPercent -> {}", differPercent);
                           revisionSalaryAmount = amount * (1 + (differPercent));
                           //log.info("revisionSalaryAmount -> {}", revisionSalaryAmount);
                           paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(revisionSalaryAmount));
                       }
                   }
               }
           }
        }
        if (Objects.equals(salaryComponentName, "TEMPORAL_SALARY")){
            // Lógica específica para el componente TEMPORAL_SALARY
            if (revisionSalaryMinEtt != null){
                if (Boolean.TRUE.equals(revisionSalaryMinEtt.getIsRetroactive())) {
                    int idxStart;
                    int idxEnd;
                    String[] periodRevisionSalary = revisionSalaryMinEtt.getPeriodRetroactive().split("-");
                    idxStart = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                            .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary[0]);
                    idxEnd = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                            .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary.length == 1 ? periodRevisionSalary[0] : periodRevisionSalary[1]);
                    AtomicReference<Double> salaryFirst = new AtomicReference<>(0.0);
                    AtomicReference<Double> salaryEnd = new AtomicReference<>(0.0);
                    salaryFirst.set(paymentComponentDTO.getProjections().get(idxStart).getAmount().doubleValue());
                    salaryEnd.set(paymentComponentDTO.getProjections().get(idxEnd).getAmount().doubleValue());
                    differPercent = (salaryEnd.get()) / (salaryFirst.get()) - 1;
                }
                double percent = revisionSalaryMinEtt.getValue() / 100;
                int idx = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()), revisionSalaryMinEtt.getPeriod());
                if (idx != -1) {
                    for (int i = idx; i < paymentComponentDTO.getProjections().size(); i++) {
                        double revisionSalaryAmount;
                        double amount = i == 0 ? paymentComponentDTO.getProjections().get(i).getAmount().doubleValue() : paymentComponentDTO.getProjections().get(i - 1).getAmount().doubleValue();
                        paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                        if (paymentComponentDTO.getProjections().get(i).getMonth().equalsIgnoreCase(revisionSalaryMinEtt.getPeriod())) {
                            // R13 * ( 1 +SI(Q13 / P13 - 1 > 0;SI(Q13 / P13 - 1 <= S$8;S$8 - ( Q13 / P13 - 1 );0);S$8 ) ))
                            if (differPercent > 0) {
                                // 9%
                                if (differPercent <= percent) {
                                    differPercent = percent - differPercent;
                                } else {
                                    differPercent = 0;
                                }
                            } else {
                                differPercent = percent;
                            }
                            //log.info("differPercent -> {}", differPercent);
                            revisionSalaryAmount = amount * (1 + (differPercent));
                            //log.info("revisionSalaryAmount -> {}", revisionSalaryAmount);
                            paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(revisionSalaryAmount));
                        }
                    }
                }
            }
        }
    }
    public void commission(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, BigDecimal sumCommission) {
        //TODO : TIENE VALOR DE COMISIONES DE SSFF
        String category = findCategory(classEmployee);
        PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
        paymentComponentDTO.setPaymentComponent("COMMISSION");
        PaymentComponentDTO salaryComponent = component.stream()
                .filter(c -> c.getPaymentComponent().equals(SALARY))
                .findFirst()
                .orElse(null);
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        paymentComponentDTO.setAmount(BigDecimal.valueOf(salary / 12));
        if (category.equals("P")) {
            // BUSCAMOS LOS PAYMENT COMPONENTS NECESARIOS PARA EL CALCUL  DE COMISIONES
            AtomicReference<BigDecimal> commision1 = new AtomicReference<>(BigDecimal.ZERO);
            AtomicReference<BigDecimal> commision2 = new AtomicReference<>(BigDecimal.ZERO);
            //TRAER COMPONENTES DE PAGO PARA CALCULAR EL SALARIO BASE PC938001 - PC938005
            component.stream()
                    .filter(p -> p.getPaymentComponent().equalsIgnoreCase("PC938003"))
                    .findFirst()
                    .ifPresent(p -> {
                        commision1.set(p.getAmount());
                    });
            component.stream()
                    .filter(p -> p.getPaymentComponent().equalsIgnoreCase("PC938012"))
                    .findFirst()
                    .ifPresent(p -> {
                        commision2.set(p.getAmount());
                    });
            List<ParametersDTO> commissionList = parameters.stream()
                    .filter(p -> Objects.equals(p.getParameter().getName(), "Comisiones (anual)"))
                    .collect(Collectors.toList());
            // Crear un componente de pago para las comisiones(sumatoria de los dos componentes)
            //PC938003 / PC938012
            //mes base -> el primero del mes
            AtomicReference<BigDecimal> maxCommission = new AtomicReference<>(BigDecimal.ZERO);
            maxCommission.set(getMayor(commision1.get(),commision2.get()));
            BigDecimal totalBase = BigDecimal.valueOf(commissionList.isEmpty()?0:commissionList.get(0).getValue());
            BigDecimal sumCommissionValue = sumCommission.doubleValue()==0?BigDecimal.ONE:sumCommission;
            paymentComponentDTO.setAmount(BigDecimal.valueOf(totalBase.doubleValue()/12*
                    maxCommission.get().doubleValue()/sumCommissionValue.doubleValue()));
            paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));

            // buscamos el primer valor del parametro de comisiones
            AtomicReference<Double> paramCommissionInitValue = new AtomicReference<>((double) 0);
            AtomicReference<String> paramCommissionInitPeriod = new AtomicReference<>((String) "");
            parameters.stream()
                    .filter(p -> p.getParameter().getId() == 28)
                    .findFirst()
                    .ifPresent(param -> {
                        paramCommissionInitValue.set
                                (param.getValue());
                    });
            AtomicReference<Double> paramValue = new AtomicReference<>(0.0);
            component.stream()
                    .parallel()
                    .forEach(c -> {
                        double defaultSum = 1.0;
                        for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
                            try {
                                ParamFilterDTO res = isRefreshCommisionValue(commissionList,paymentComponentDTO.getProjections().get(i).getMonth());
                                if (Boolean.TRUE.equals(res.getStatus())) paramValue.set(res.getValue());
                                double sum = sumCommission.doubleValue()==0?defaultSum:sumCommission.doubleValue();
                                double vc = maxCommission.get().doubleValue()/sum;
                                double vd = paramValue.get()/12;
                                double v = vc * vd;
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(v));
                            }catch (Exception e){
                                log.error("error commission -> {}", e.getMessage());
                            }
                        }
                    });
        } else {
            paymentComponentDTO.setAmount(BigDecimal.valueOf(0));
            paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
        }
        component.add(paymentComponentDTO);
    }
    public static ParamFilterDTO isRefreshCommisionValue(List<ParametersDTO> params, String periodProjection){
        AtomicReference<Double> value = new AtomicReference<>(0.0);
        Boolean status = params.stream()
                .parallel()
                .anyMatch(p -> {
                    DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                            .appendPattern(TYPEMONTH)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter();
                    LocalDate dateParam = LocalDate.parse(p.getPeriod(),dateFormat);
                    LocalDate dateProjection = LocalDate.parse(periodProjection, dateFormat);
                    value.set(p.getValue());
                    return dateParam.equals(dateProjection);
                });
    return ParamFilterDTO.builder()
            .status(status)
            .value(value.get())
            .build();
    }
    public void prodMonthPrime(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        // Obtener los componentes necesarios para el cálculo
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        // Calcular la suma de los componentes
        double totalbase = salary + overtime + surcharges + commission;
        // Calcular la provisión mensual de la prima
        BigDecimal monthlyProvisionBase = BigDecimal.valueOf(totalbase / 12);
        // Crear un nuevo PaymentComponentDTO para la prima mensual
        PaymentComponentDTO monthlyPrimeComponent = new PaymentComponentDTO();
        monthlyPrimeComponent.setPaymentComponent("PRIMA MENSUAL");
        monthlyPrimeComponent.setAmount(monthlyProvisionBase);
        if (salaryComponent != null) {
            if (category.equals("P") &&  salaryComponent.getSalaryType().equals("BASE")) {
                // Calcular la prima mensual para cada proyección
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    BigDecimal totalAmount = Stream.of(salaryComponent, overtimeComponent, surchargesComponent, commissionComponent)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .map(MonthProjection::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Calcular la provisión mensual de la prima
                    BigDecimal monthlyProvision = BigDecimal.valueOf(totalAmount.doubleValue() / 12);

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(monthlyProvision);
                    projections.add(projection);
                }
                monthlyPrimeComponent.setProjections(projections);
            } else {
                monthlyPrimeComponent.setAmount(BigDecimal.valueOf(0));
                monthlyPrimeComponent.setProjections(Shared.generateMonthProjection(period,range,monthlyPrimeComponent.getAmount()));
            }
        }else {
            monthlyPrimeComponent.setAmount(BigDecimal.valueOf(0));
            monthlyPrimeComponent.setProjections(Shared.generateMonthProjection(period,range,monthlyPrimeComponent.getAmount()));
        }
        component.add(monthlyPrimeComponent);
    }

    private BigDecimal getMayor(BigDecimal b1 , BigDecimal b2){
         return b1.doubleValue()>b2.doubleValue()?b1:b2;
    }
    public void consolidatedVacation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        // Obtén los componentes necesarios para el cálculo
        List<String> vacationComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION","SALARY_PRA","TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> vacationComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        if (category.equals("APR") || category.equals("PRA")){
            salaryComponent = componentMap.get(SALARY_PRA);
        }
        if (!category.equals("T")) {
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            // Crear un nuevo PaymentComponentDTO para el Consolidado de Vacaciones
            PaymentComponentDTO vacationComponent = new PaymentComponentDTO();
            vacationComponent.setPaymentComponent("CONSOLIDADO_VACACIONES");
            double totalAmountBase = salary + overtime + surcharges + commission;
            vacationComponent.setAmount(BigDecimal.valueOf(totalAmountBase / 24));
            // Calcular el Consolidado de Vacaciones para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : componentMap.get("SALARY").getProjections()) {
                BigDecimal totalAmount = vacationComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .map(MonthProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calcular el costo del Consolidado de Vacaciones
                BigDecimal vacationCost = BigDecimal.valueOf(totalAmount.doubleValue() / 24);
                //log.info("vacationCost -> {}", vacationCost);
                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(vacationCost);
                projections.add(projection);
            }
            vacationComponent.setProjections(projections);
            component.add(vacationComponent);
        }else {
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent("CONSOLIDADO_VACACIONES");
            paymentComponentDTO.setAmount(BigDecimal.valueOf(0));
            paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
    }
    public void consolidatedSeverance(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        List<String> severanceComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION","SALARY_PRA","TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> severanceComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
         if (category.equals("APR") || category.equals("PRA")){
            salaryComponent = componentMap.get(SALARY_PRA);
        }
        if (!category.equals("T") && salaryComponent.getSalaryType().equals("BASE")) {
            // Obtén los componentes necesarios para el cálculo
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
            double salary = salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            // Crear un nuevo PaymentComponentDTO para el Consolidado de Cesantías
            PaymentComponentDTO severanceComponent = new PaymentComponentDTO();
            severanceComponent.setPaymentComponent("CONSOLIDADO_CESANTIAS");
            double salaryBase = salary + overtime + surcharges + commission;
            BigDecimal totalAmountBase = BigDecimal.valueOf(salaryBase / 12);
            severanceComponent.setAmount(totalAmountBase);
            // Calcular el Consolidado de Cesantías para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                BigDecimal totalAmount = severanceComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .map(MonthProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calcular el costo del Consolidado de Cesantías
                BigDecimal severanceCost = totalAmount.divide(BigDecimal.valueOf(12), BigDecimal.ROUND_HALF_UP);

                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(severanceCost);
                projections.add(projection);
            }
            severanceComponent.setProjections(projections);
            component.add(severanceComponent);
        }else {
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent("CONSOLIDADO_CESANTIAS");
            paymentComponentDTO.setAmount(BigDecimal.valueOf(0));
            paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
    }
    public void consolidatedSeveranceInterest(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        Map<String, PaymentComponentDTO> componentMapBase = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMapBase.get(SALARY);;
        if (category.equals("APR") || category.equals("PRA")){
            salaryComponent = componentMapBase.get(SALARY_PRA);
        }
        // Obtén el componente necesario para el cálculo
        PaymentComponentDTO severanceComponent = componentMapBase.get("CONSOLIDADO_CESANTIAS");
        if (!category.equals("T") && salaryComponent.getSalaryType().equals("BASE")) {

            // Crear un nuevo PaymentComponentDTO para el Consolidado de Intereses de Cesantías
            PaymentComponentDTO severanceInterestComponent = new PaymentComponentDTO();
            severanceInterestComponent.setPaymentComponent("CONSOLIDADO_INTERESES_CESANTIAS");
            severanceInterestComponent.setAmount(BigDecimal.valueOf(severanceComponent.getAmount().doubleValue() * 0.12));
            // Calcular el Consolidado de Intereses de Cesantías para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : severanceComponent.getProjections()) {
                BigDecimal severanceAmount = primeProjection.getAmount();

                // Calcular el 12% del Consolidado de Cesantías
                BigDecimal severanceInterest = severanceAmount.multiply(BigDecimal.valueOf(0.12));

                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(severanceInterest);
                projections.add(projection);
            }
            severanceInterestComponent.setProjections(projections);
            component.add(severanceInterestComponent);
        }else {
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent("CONSOLIDADO_INTERESES_CESANTIAS");
            paymentComponentDTO.setAmount(BigDecimal.valueOf(0));
            paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
    }
    public void transportSubsidy(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        // Obtén los componentes necesarios para el cálculo
        List<String> subsidyComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION", "SALARY_PRA", "TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> subsidyComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        ParametersDTO subsidyMin = getParametersById(parameters, 48);
        double subsidyMinInternal = subsidyMin!=null ? subsidyMin.getValue() : 0.0;
        //total base
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        // Crear un nuevo PaymentComponentDTO para el Subsidio de Transporte
        PaymentComponentDTO transportSubsidyComponent = new PaymentComponentDTO();
        transportSubsidyComponent.setPaymentComponent("SUBSIDIO_TRANSPORTE");
        double totalAmountBase = salary + overtime + surcharges + commission;
        transportSubsidyComponent.setAmount(BigDecimal.valueOf(totalAmountBase < 2 * legalSalaryMinInternal ? subsidyMinInternal : 0));
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        if (category.equals("P") && salaryType.equals("BASE")) {
            // Calcular el Subsidio de Transporte para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                BigDecimal totalAmount = subsidyComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .map(MonthProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calcular el costo del Subsidio de Transporte
                BigDecimal transportSubsidyCost;
                if (totalAmount.doubleValue() < 2 * legalSalaryMinInternal) {
                    transportSubsidyCost = BigDecimal.valueOf(subsidyMinInternal);
                } else {
                    transportSubsidyCost = BigDecimal.ZERO;
                }

                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(transportSubsidyCost);
                projections.add(projection);
            }
            transportSubsidyComponent.setProjections(projections);
        }else {
            transportSubsidyComponent.setAmount(BigDecimal.valueOf(0));
            transportSubsidyComponent.setProjections(Shared.generateMonthProjection(period,range,transportSubsidyComponent.getAmount()));
        }
        component.add(transportSubsidyComponent);
    }
    public void contributionBox(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        // Obtén los componentes necesarios para el cálculo
        List<String> boxComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION", "SALARY_PRA", "TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> boxComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        // Crear un nuevo PaymentComponentDTO para el Aporte a la Caja
        PaymentComponentDTO boxContributionComponent = new PaymentComponentDTO();
        boxContributionComponent.setPaymentComponent("APORTE_CAJA");
        double totalAmountBase = salary + overtime + surcharges + commission;
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        boxContributionComponent.setAmount(BigDecimal.valueOf(salaryType.equals("BASE") ? totalAmountBase * 0.04 : totalAmountBase * 0.70 * 0.04));
        if (category.equals("P")) {
            // Calcular el Aporte a la Caja para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                BigDecimal totalAmount = boxComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .map(MonthProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calcular el Aporte a la Caja
                BigDecimal boxContribution;
                if (salaryComponent.getSalaryType().equals("BASE")) {
                    boxContribution = totalAmount.multiply(BigDecimal.valueOf(0.04));
                } else {
                    boxContribution = totalAmount.multiply(BigDecimal.valueOf(0.70)).multiply(BigDecimal.valueOf(0.04));
                }

                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(boxContribution);
                projections.add(projection);
            }

            boxContributionComponent.setProjections(projections);
        }else {
            boxContributionComponent.setAmount(BigDecimal.valueOf(0));
            boxContributionComponent.setProjections(Shared.generateMonthProjection(period,range,boxContributionComponent.getAmount()));
        }
        component.add(boxContributionComponent);
    }
    public void companyHealthContribution(List<PaymentComponentDTO> component, String classEmployee,  List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        List<String> healthComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION", "SALARY_PRA", "TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> healthComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        if (category.equals("APR") || category.equals("PRA")){
            salaryComponent = componentMap.get(SALARY_PRA);
        }
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        // Crear un nuevo PaymentComponentDTO para el Aporte Salud Empresa
        PaymentComponentDTO healthContributionComponent = new PaymentComponentDTO();
        healthContributionComponent.setPaymentComponent("APORTE_SALUD_EMPRESA");
        double totalAmountBase = salary + overtime + surcharges + commission;
       // log.info("totalAmountBase -> {}", totalAmountBase);
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        healthContributionComponent.setAmount(BigDecimal.valueOf(salaryType.equals("BASE") ? totalAmountBase * 0.085 : totalAmountBase * 0.70 * 0.085));
        if (!category.equals("T")) {
            // Calcular el Aporte Salud Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    BigDecimal totalAmount = healthComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .map(MonthProjection::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Calcular el Aporte Salud Empresa
                    BigDecimal healthContribution;
                    if (category.equals("APR") || category.equals("PRA")) {
                        healthContribution = totalAmount.multiply(BigDecimal.valueOf(0.04));
                    } else if (category.equals("P")) {
                        if (salaryComponent.getSalaryType().equals("BASE")) {
                            if (totalAmount.doubleValue() > 25 * legalSalaryMinInternal) {
                                //healthContribution = legalSalaryMinInternal.multiply(BigDecimal.valueOf(25)).multiply(BigDecimal.valueOf(0.085));
                                healthContribution = BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.085);
                            } else if (totalAmount.doubleValue() > 10 * legalSalaryMinInternal) {
                                //healthContribution = totalAmount.multiply(BigDecimal.valueOf(0.085));
                                healthContribution = BigDecimal.valueOf(totalAmount.doubleValue() * 0.085);
                            } else {
                                healthContribution = BigDecimal.ZERO;
                            }
                        } else { // salaryType is INTEGRAL
                            BigDecimal seventyPercentTotal = totalAmount.multiply(BigDecimal.valueOf(0.70));
                            double seventyPercentTotalDouble = seventyPercentTotal.doubleValue();
                            if (seventyPercentTotalDouble > 25 * legalSalaryMinInternal) {
                                //healthContribution = legalSalaryMinInternal.multiply(BigDecimal.valueOf(25)).multiply(BigDecimal.valueOf(0.085));
                                healthContribution = BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.085);
                            } else {
                                //healthContribution = seventyPercentTotal.multiply(BigDecimal.valueOf(0.085));
                                healthContribution = BigDecimal.valueOf(seventyPercentTotalDouble * 0.085);
                            }
                        }
                    } else {
                        healthContribution = BigDecimal.ZERO;
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(healthContribution);
                    projections.add(projection);
                }
                healthContributionComponent.setProjections(projections);
            }else {
                healthContributionComponent.setAmount(BigDecimal.valueOf(0));
                healthContributionComponent.setProjections(Shared.generateMonthProjection(period,range,healthContributionComponent.getAmount()));
            }
        }else {
            healthContributionComponent.setAmount(BigDecimal.valueOf(0));
            healthContributionComponent.setProjections(Shared.generateMonthProjection(period,range,healthContributionComponent.getAmount()));
        }
        component.add(healthContributionComponent);
    }
    public void companyRiskContribution(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> riskComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> riskComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        // Crear un nuevo PaymentComponentDTO para el Aporte Riesgo Empresa
        PaymentComponentDTO riskContributionComponent = new PaymentComponentDTO();
        riskContributionComponent.setPaymentComponent("APORTE_RIESGO_EMPRESA");
        double totalAmountBase = salary + overtime + surcharges + commission;
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        riskContributionComponent.setAmount(BigDecimal.valueOf(salaryType.equals("BASE") ? totalAmountBase * 0.00881 : totalAmountBase * 0.70 * 0.00881));
        if (category.equals("P")) {
            // Calcular el Aporte Riesgo Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection riskProjection : salaryComponent.getProjections()) {
                    double totalAmount = riskComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(riskProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Riesgo Empresa
                    double riskContribution;
                    if (salaryComponent.getSalaryType().equals("BASE")) {
                        if (totalAmount > 25 * legalSalaryMinInternal) {
                            riskContribution = 25 * legalSalaryMinInternal * 0.00881;
                        } else {
                            riskContribution = totalAmount * 0.00881;
                        }
                    } else { // salaryType is INTEGRAL
                        double adjustedTotalAmount = totalAmount * 0.7;
                        if (adjustedTotalAmount > 25 * legalSalaryMinInternal) {
                            riskContribution = 25 * legalSalaryMinInternal * 0.00881;
                        } else {
                            riskContribution = adjustedTotalAmount * 0.00881;
                        }
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(riskProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(riskContribution));
                    projections.add(projection);
                }
                riskContributionComponent.setProjections(projections);
            }else {
                riskContributionComponent.setAmount(BigDecimal.valueOf(0));
                riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
            }
        }else {
            riskContributionComponent.setAmount(BigDecimal.valueOf(0));
            riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
        }
        component.add(riskContributionComponent);
    }
    public void companyRiskContributionTrainee(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> riskComponents = Arrays.asList("SALARY_PRA", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> riskComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY_PRA");
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        double totalAmountBase = salary + overtime + surcharges + commission;
        // Crear un nuevo PaymentComponentDTO para el Aporte Riesgo Empresa
        PaymentComponentDTO riskContributionComponent = new PaymentComponentDTO();
        riskContributionComponent.setPaymentComponent("APORTE_RIESGO_EMPRESA_BECARIOS");
        riskContributionComponent.setAmount(totalAmountBase > 25 * legalSalaryMinInternal ? BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.00881) : BigDecimal.valueOf(totalAmountBase * 0.00881));
        if (category.equals("APR") || category.equals("PRA")) {
            // Calcular el Aporte Riesgo Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection riskProjection : salaryComponent.getProjections()) {
                    double totalAmount = riskComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(riskProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Riesgo Empresa
                    double riskContribution;
                    if (totalAmount > 25 * legalSalaryMinInternal) {
                        riskContribution = 25 * legalSalaryMinInternal * 0.00881;
                    } else {
                        riskContribution = totalAmount * 0.00881;
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(riskProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(riskContribution));
                    projections.add(projection);
                }
                riskContributionComponent.setProjections(projections);
            }else {
                riskContributionComponent.setAmount(BigDecimal.valueOf(0));
                riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
            }
        }else {
            riskContributionComponent.setAmount(BigDecimal.valueOf(0));
            riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
        }
        component.add(riskContributionComponent);
    }
    public void companyRiskContributionTemporaries(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        //TODO : CAMBIAR COMMISION POR COMMISSION TEMPORAL
        List<String> riskComponents = Arrays.asList(TEMPORAL_SALARY, "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> riskComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get(TEMPORAL_SALARY);
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        double totalAmountBase = salary + overtime + surcharges + commission;
        // Crear un nuevo PaymentComponentDTO para el Aporte Riesgo Empresa
        PaymentComponentDTO riskContributionComponent = new PaymentComponentDTO();
        riskContributionComponent.setPaymentComponent("APORTE_RIESGO_EMPRESA_TEMPORALES");
        riskContributionComponent.setAmount(totalAmountBase > 25 * legalSalaryMinInternal ? BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.00881) : BigDecimal.valueOf(totalAmountBase * 0.00881));
        if (category.equals("T")) {
            // Calcular el Aporte Riesgo Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection riskProjection : salaryComponent.getProjections()) {
                    double totalAmount = riskComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(riskProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Riesgo Empresa
                    double riskContribution;
                    if (totalAmount > 25 * legalSalaryMinInternal) {
                        riskContribution = 25 * legalSalaryMinInternal * 0.00881;
                    } else {
                        riskContribution = totalAmount * 0.00881;
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(riskProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(riskContribution));
                    projections.add(projection);
                }
                riskContributionComponent.setProjections(projections);
            }else {
                riskContributionComponent.setAmount(BigDecimal.valueOf(0));
                riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
            }
        }else {
            riskContributionComponent.setAmount(BigDecimal.valueOf(0));
            riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
        }
        component.add(riskContributionComponent);
    }
    public void icbfContribution(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> icbfComponents = Arrays.asList(SALARY, "HHEE", "SURCHARGES", "COMMISSION", SALARY_PRA);
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> icbfComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        if (category.equals("APR") || category.equals("PRA")){
            salaryComponent = componentMap.get(SALARY_PRA);
        }
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        double totalAmountBase = salary + overtime + surcharges + commission;
        // Crear un nuevo PaymentComponentDTO para el Aporte Icbf
        PaymentComponentDTO icbfContributionComponent = new PaymentComponentDTO();
        icbfContributionComponent.setPaymentComponent("APORTE_ICBF");
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        double icbfContributionBase;
        if (salaryType.equals("BASE") && totalAmountBase > 10 * legalSalaryMinInternal) {
            icbfContributionBase = totalAmountBase * 0.03;
        } else if (!salaryType.equals("BASE")) {
            icbfContributionBase = totalAmountBase * 0.70 * 0.03;
        } else {
            icbfContributionBase = 0;
        }
        icbfContributionComponent.setAmount(BigDecimal.valueOf(icbfContributionBase));
        if (!category.equals("T")) {
            // Calcular el Aporte Icbf para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = icbfComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Icbf
                    double icbfContribution;
                    if (salaryComponent.getSalaryType().equals("BASE") && totalAmount > 10 * legalSalaryMinInternal) {
                        icbfContribution = totalAmount * 0.03;
                    } else if (!salaryComponent.getSalaryType().equals("BASE")) {
                        icbfContribution = totalAmount * 0.70 * 0.03;
                    } else {
                        icbfContribution = 0;
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(icbfContribution));
                    projections.add(projection);
                }
                icbfContributionComponent.setProjections(projections);
            }else {
                icbfContributionComponent.setAmount(BigDecimal.valueOf(0));
                icbfContributionComponent.setProjections(Shared.generateMonthProjection(period,range,icbfContributionComponent.getAmount()));
            }
        }else {
            icbfContributionComponent.setAmount(BigDecimal.valueOf(0));
            icbfContributionComponent.setProjections(Shared.generateMonthProjection(period,range,icbfContributionComponent.getAmount()));
        }
        component.add(icbfContributionComponent);
    }
    public void senaContribution(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> senaComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> senaComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        if (category.equals("APR") || category.equals("PRA")){
            salaryComponent = componentMap.get(SALARY_PRA);
        }
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        double totalAmountBase = salary + overtime + surcharges + commission;
        // Crear un nuevo PaymentComponentDTO para el Aporte Sena
        PaymentComponentDTO senaContributionComponent = new PaymentComponentDTO();
        senaContributionComponent.setPaymentComponent("APORTE_SENA");
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        senaContributionComponent.setAmount(BigDecimal.valueOf(salaryType.equals("BASE") && totalAmountBase > 10 * legalSalaryMinInternal ? totalAmountBase * 0.02 : totalAmountBase * 0.70 * 0.02));
        if (!category.equals("T")) {
            // Calcular el Aporte Sena para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = senaComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Sena
                    double senaContribution;
                    if (salaryComponent.getSalaryType().equals("BASE") && totalAmount > 10 * legalSalaryMinInternal) {
                        senaContribution = totalAmount * 0.02;
                    } else if (!salaryComponent.getSalaryType().equals("BASE")) {
                        senaContribution = totalAmount * 0.70 * 0.02;
                    } else {
                        senaContribution = 0;
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(senaContribution));
                    projections.add(projection);
                }
                senaContributionComponent.setProjections(projections);
            }else {
                senaContributionComponent.setAmount(BigDecimal.valueOf(0));
                senaContributionComponent.setProjections(Shared.generateMonthProjection(period,range,senaContributionComponent.getAmount()));
            }
        }else {
            senaContributionComponent.setAmount(BigDecimal.valueOf(0));
            senaContributionComponent.setProjections(Shared.generateMonthProjection(period,range,senaContributionComponent.getAmount()));
        }
        component.add(senaContributionComponent);
    }
    public void companyPensionContribution(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> pensionComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> pensionComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        double totalAmountBase = salary + overtime + surcharges + commission;
// Crear un nuevo PaymentComponentDTO para el Aporte Pensión Empresa
        PaymentComponentDTO pensionContributionComponent = new PaymentComponentDTO();
        pensionContributionComponent.setPaymentComponent("APORTE_PENSION_EMPRESA");
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        pensionContributionComponent.setAmount(BigDecimal.valueOf(salaryType.equals("BASE") ? totalAmountBase * 0.12 : totalAmountBase * 0.70 * 0.12));
        if (category.equals("P")) {
            // Calcular el Aporte Pensión Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = pensionComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Pensión Empresa
                    double pensionContribution;
                    if (salaryComponent.getSalaryType().equals("BASE") && totalAmount > 25 * legalSalaryMinInternal) {
                        pensionContribution = 25 * legalSalaryMinInternal * 0.12;
                    } else if (!salaryComponent.getSalaryType().equals("BASE")) {
                        pensionContribution = totalAmount * 0.70 * 0.12;
                    } else {
                        pensionContribution = totalAmount * 0.12;
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(pensionContribution));
                    projections.add(projection);
                }
                pensionContributionComponent.setProjections(projections);
            }else {
                pensionContributionComponent.setAmount(BigDecimal.valueOf(0));
                pensionContributionComponent.setProjections(Shared.generateMonthProjection(period,range,pensionContributionComponent.getAmount()));
            }
        }else {
            pensionContributionComponent.setAmount(BigDecimal.valueOf(0));
            pensionContributionComponent.setProjections(Shared.generateMonthProjection(period,range,pensionContributionComponent.getAmount()));
        }
        component.add(pensionContributionComponent);
    }

    public void sodexo(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
        ParametersDTO sodexo = getParametersById(parameters, 49);
        double sodexoValue = sodexo != null ? sodexo.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> sodexoComponents = Arrays.asList("SALARY", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> sodexoComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        double totalAmountBase = salary + commission;
        // Crear un nuevo PaymentComponentDTO para Sodexo
        PaymentComponentDTO sodexoComponent = new PaymentComponentDTO();
        sodexoComponent.setPaymentComponent("SODEXO");
        sodexoComponent.setAmount(BigDecimal.valueOf((category.equals("P") || category.equals("APR") || category.equals("PRA")) && totalAmountBase < 2 * legalSalaryMinInternal ? sodexoValue : 0));
        if (!category.equals("T")) {
            // Calcular el valor de Sodexo para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = sodexoComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();
                    // Calcular el valor de Sodexo
                    double sodexoContribution;
                    if ((category.equals("P") || category.equals("APR") || category.equals("PRA")) && totalAmount < 2 * legalSalaryMinInternal) {
                        sodexoContribution = sodexoValue;
                    } else {
                        sodexoContribution = 0;
                    }
                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(sodexoContribution));
                    projections.add(projection);
                }
                sodexoComponent.setProjections(projections);
            }else {
                sodexoComponent.setAmount(BigDecimal.valueOf(0));
                sodexoComponent.setProjections(Shared.generateMonthProjection(period,range,sodexoComponent.getAmount()));
            }
        }else {
            sodexoComponent.setAmount(BigDecimal.valueOf(0));
            sodexoComponent.setProjections(Shared.generateMonthProjection(period,range,sodexoComponent.getAmount()));
        }
        component.add(sodexoComponent);
    }
}
