package ms.hispam.budget.rules.operations.salary;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.rules.countries.Country;
import ms.hispam.budget.rules.operations.Mediator;
import ms.hispam.budget.rules.operations.Operation;
import ms.hispam.budget.util.Shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
@Slf4j(topic = "Peru")
public class Peru implements Country, Mediator {
    private final List<Operation> operations;
    static final String TYPEMONTH="yyyyMM";
    private static final String THEORETICAL_SALARY = "THEORETICAL-SALARY";
    private static final String VACATION_ENJOYMENT = "VACATION-ENJOYMENT";
    private static final String BASE_SALARY = "BASE-SALARY";
    private static final String VACATION_PROVISION = "VACATION-PROVISION";
    private static final String GRATIFICATIONS = "GRATIFICATIONS";
    private static final String PC960400 = "PC960400";
    private static final String PC960401 = "PC960401";

    public Peru(List<Operation> operations) {
        this.operations = operations;
    }

    @Override
    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        for (Operation operation : operations) {
            operation.execute(this, component, parameters, classEmployee, period, range);
        }
    }
    @Override
    public void executeOperation(Operation operation, List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        if (operation instanceof SalaryOperationPeru) {
            processSalaryOperation(component, parameters, period, range);
        } else if (operation instanceof RevisionSalaryOperationPeru) {
            ParametersDTO salaryParameters = getParametersById(parameters, classEmployee.equals("emp") ? 34 : 35);
            if (salaryParameters != null) processRevisionSalaryOperation(component, parameters,salaryParameters);
        }else if (operation instanceof VacationEnjoymentOperation) {
             processVacationEnjoymentOperation(component, parameters);
        }else if (operation instanceof BaseSalaryOperation) {
            processBaseSalaryOperation(component, parameters);
        }else if (operation instanceof VacationProvisionOperation) {
            processVacationProvisionOperation(component, parameters);
        }else if(operation instanceof GratificationOperation) {
            processGratificationsOperation(component);
        }
    }

    private void processBaseSalaryOperation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
       // Obtener los PaymentComponentDTO para THEORETICAL_SALARY y VACATION_ENJOYMENT
        PaymentComponentDTO theoreticalSalaryComponent = componentMap.get(THEORETICAL_SALARY);
        PaymentComponentDTO vacationEnjoymentComponent = componentMap.get(VACATION_ENJOYMENT);
        log.info("{}", theoreticalSalaryComponent);
        log.info("{}", vacationEnjoymentComponent);
        if (theoreticalSalaryComponent != null && vacationEnjoymentComponent != null) {
            // Crear el PaymentComponentDTO para baseSalary
            PaymentComponentDTO baseSalaryComponent = new PaymentComponentDTO();
            baseSalaryComponent.setPaymentComponent(BASE_SALARY);
            // Calcular el valor de baseSalary a partir de THEORETICAL_SALARY y VACATION_ENJOYMENT
            double theoreticalSalaryComp = theoreticalSalaryComponent.getAmount().doubleValue();
            double vacationEnjoymentComp = vacationEnjoymentComponent.getAmount().doubleValue();
            baseSalaryComponent.setAmount(BigDecimal.valueOf(theoreticalSalaryComp - vacationEnjoymentComp));
            // Iterar sobre las proyecciones de THEORETICAL_SALARY y VACATION_ENJOYMENT
            List<MonthProjection> projections = new ArrayList<>();
            for (int i = 0; i < theoreticalSalaryComponent.getProjections().size(); i++) {
                MonthProjection theoreticalSalaryProjection = theoreticalSalaryComponent.getProjections().get(i);
                MonthProjection vacationEnjoymentProjection = vacationEnjoymentComponent.getProjections().get(i);
                // Calcular el valor de BASE_SALARY para el mes
                double theoreticalSalary = theoreticalSalaryProjection.getAmount().doubleValue();
                double vacationEnjoyment = vacationEnjoymentProjection.getAmount().doubleValue();
                double baseSalary = theoreticalSalary - vacationEnjoyment;
                // Crear una nueva proyección para BASE_SALARY
                MonthProjection baseSalaryProjection = new MonthProjection();
                baseSalaryProjection.setMonth(theoreticalSalaryProjection.getMonth());
                baseSalaryProjection.setAmount(BigDecimal.valueOf(baseSalary));
                // Agregar la proyección a BASE_SALARY
                projections.add(baseSalaryProjection);
            }
            baseSalaryComponent.setProjections(projections);
            // Agregar baseSalaryComponent a la lista de componentes
            component.add(baseSalaryComponent);
        }
    }
    private void processGratificationsOperation(List<PaymentComponentDTO> component) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        // Obtener el PaymentComponentDTO para THEORETICAL_SALARY
        PaymentComponentDTO theoreticalSalaryComponent = componentMap.get(THEORETICAL_SALARY);
        if (theoreticalSalaryComponent != null) {
            // Crear el PaymentComponentDTO para GRATIFICATIONS
            PaymentComponentDTO gratificationsComponent = new PaymentComponentDTO();
            gratificationsComponent.setPaymentComponent(GRATIFICATIONS);
            // Crear una lista para las proyecciones de GRATIFICATIONS
            List<MonthProjection> gratificationsProjections = new ArrayList<>();
            // Iterar sobre las proyecciones de THEORETICAL_SALARY
            for (MonthProjection theoreticalSalaryProjection : theoreticalSalaryComponent.getProjections()) {
                double theoreticalSalary = theoreticalSalaryProjection.getAmount().doubleValue();
                // Calcular GRATIFICATIONS usando la fórmula (AA15*2/12)
                double gratificationsValue = (theoreticalSalary * 2) / 12;
                // Crear una nueva proyección para GRATIFICATIONS
                MonthProjection gratificationsProjection = new MonthProjection();
                gratificationsProjection.setMonth(theoreticalSalaryProjection.getMonth());
                gratificationsProjection.setAmount(BigDecimal.valueOf(gratificationsValue));
                // Agregar la proyección a la lista de proyecciones de GRATIFICATIONS
                gratificationsProjections.add(gratificationsProjection);
            }
            // Establecer las proyecciones de GRATIFICATIONS en gratificationsComponent
            gratificationsComponent.setProjections(gratificationsProjections);
            // Agregar GRATIFICATIONS a la lista de componentes
            component.add(gratificationsComponent);
        }
    }
    private void processVacationEnjoymentOperation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters) {
       //(AC15/30)*$Z$7*$V4*AC$8
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get(THEORETICAL_SALARY);
        //TODO: DEFAULT VACATION DAYS 30
        //TODO: DEFAULT VACATION SEASONALITY 8.33
        if (salaryComponent != null){
            ParametersDTO vacationDays = getParametersById(parameters, 39);
            ParametersDTO vacationSeasonality = getParametersById(parameters, 40);
            double vacationDaysValue = 30;
            if (vacationDays != null) vacationDaysValue = vacationDays.getValue();
            double vacationSeasonalityValue = 8.33;
            if (vacationSeasonality != null) vacationSeasonalityValue = vacationSeasonality.getValue() / 100;
            // Crear el PaymentComponentDTO para vacationEnjoyment
            PaymentComponentDTO vacationEnjoymentComponent = new PaymentComponentDTO();
            vacationEnjoymentComponent.setPaymentComponent(VACATION_ENJOYMENT);
            vacationEnjoymentComponent.setAmount(BigDecimal.valueOf((salaryComponent.getAmount().doubleValue() / 30) * vacationDaysValue * vacationSeasonalityValue));
            //log.info("{}", salaryComponent.getProjections());
            List<MonthProjection> projections = new ArrayList<>();
            // Iterar sobre las proyecciones de THEORETICAL_SALARY
            for (MonthProjection projection : salaryComponent.getProjections()) {
                double amount = projection.getAmount().doubleValue();
                double value = (amount / 30) * vacationDaysValue * vacationSeasonalityValue;
                // Crear una nueva proyección para vacationEnjoyment
                MonthProjection vacationEnjoymentProjection = new MonthProjection();
                vacationEnjoymentProjection.setMonth(projection.getMonth());
                vacationEnjoymentProjection.setAmount(BigDecimal.valueOf(value));
                // Agregar la proyección a vacationEnjoymentComponent
                projections.add(vacationEnjoymentProjection);
            }
            vacationEnjoymentComponent.setProjections(projections);
            // Agregar vacationEnjoymentComponent a la lista de componentes
            component.add(vacationEnjoymentComponent);
        }
    }
    private void processVacationProvisionOperation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        // Obtener el PaymentComponentDTO para THEORETICAL_SALARY
        PaymentComponentDTO theoreticalSalaryComponent = componentMap.get(THEORETICAL_SALARY);
        // Obtener el parámetro Días vacaciones
        ParametersDTO vacationDaysParameter = getParametersById(parameters, 39);
        if (theoreticalSalaryComponent != null) {
            //TODO: ADD VACATION DAYS PARAMETER DEFAULT 30
            double vacationDays = vacationDaysParameter != null ? vacationDaysParameter.getValue() : 30;
            // Crear el PaymentComponentDTO para VACATION-PROVISION
            PaymentComponentDTO vacationProvisionComponent = new PaymentComponentDTO();
            vacationProvisionComponent.setPaymentComponent(VACATION_PROVISION);
            vacationProvisionComponent.setAmount(BigDecimal.valueOf((theoreticalSalaryComponent.getAmount().doubleValue()/30)*(vacationDays/12)));
            vacationProvisionComponent.setProjections(new ArrayList<>());
            // Iterar sobre las proyecciones de THEORETICAL_SALARY
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection theoreticalSalaryProjection : theoreticalSalaryComponent.getProjections()) {
                double theoreticalSalary = theoreticalSalaryProjection.getAmount().doubleValue();
                // Calcular VACATION-PROVISION usando la fórmula (AA15/30)*$Z$7/12
                double vacationProvisionValue = (theoreticalSalary / 30) * (vacationDays / 12);
                // Crear una nueva proyección para VACATION-PROVISION
                MonthProjection vacationProvisionProjection = new MonthProjection();
                vacationProvisionProjection.setMonth(theoreticalSalaryProjection.getMonth());
                vacationProvisionProjection.setAmount(BigDecimal.valueOf(vacationProvisionValue));
                // Agregar la proyección a VACATION-PROVISION
                projections.add(vacationProvisionProjection);
            }
            vacationProvisionComponent.setProjections(projections);
            // Agregar VACATION-PROVISION a la lista de componentes
            component.add(vacationProvisionComponent);
        }
    }
    @Override
    public void revisionSalary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        RevisionSalaryOperationPeru operation = new RevisionSalaryOperationPeru();
        operation.execute(this,component, parameters, classEmployee, period, range);
    }

    @Override
    public void vacationEnjoyment(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        VacationEnjoymentOperation operation = new VacationEnjoymentOperation();
        operation.execute(this,component, parameters, classEmployee, period, range);
    }

    @Override
    public void baseSalary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        BaseSalaryOperation operation = new BaseSalaryOperation();
        operation.execute(this,component, parameters, classEmployee, period, range);
    }

    private void processSalaryOperation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc960400Component = componentMap.get(PC960400);
        PaymentComponentDTO pc960401Component = componentMap.get(PC960401);
        PaymentComponentDTO promoComponent = componentMap.get("promo");
        PaymentComponentDTO theoreticalSalaryComponent = createTheoreticalSalaryComponent(pc960400Component, pc960401Component, period, range);
        ParametersDTO promotionParameter = getParametersById(parameters, 38);
        double percentPromotion = 0.0;
        if (promotionParameter != null) percentPromotion = promotionParameter.getValue() / 100;
        //AA15*(1+SI($M4="emp";AB$2;AB$3)+AB$5)*(1+SI($W4<>"";SI($W4<=AB$11;SI(MES($W4)=MES(AB$11);$Z$6;0);0);0))
        if (theoreticalSalaryComponent.getProjections() != null) {
            for (int i = 0; i < theoreticalSalaryComponent.getProjections().size(); i++) {
                double amount = i == 0 ? theoreticalSalaryComponent.getProjections().get(i).getAmount().doubleValue() : theoreticalSalaryComponent.getProjections().get(i - 1).getAmount().doubleValue();
                double promoAmount;
                double total;
                if (promoComponent != null && promoComponent.getAmountString() != null && !promoComponent.getAmountString().isEmpty()){
                    DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                            .appendPattern(TYPEMONTH)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter();
                    LocalDate promoDate = LocalDate.parse(promoComponent.getAmountString(), dateFormat);
                    LocalDate date = LocalDate.parse(theoreticalSalaryComponent.getProjections().get(i).getMonth(), dateFormat);
                    // compara fechas SI($W4<=AC$11;SI(MES($W4)=MES(AC$11);$Z$6;0);0)
                    // $W4 = promoDate
                    // AC$11 = date
                    if (promoDate.isBefore(date) || promoDate.isEqual(date)) {
                        promoAmount = percentPromotion;
                    }else {
                        promoAmount = 0.0;
                    }
                }else {
                    promoAmount = 0.0;
                }
                total = amount * (1 + promoAmount);
                theoreticalSalaryComponent.getProjections().get(i).setAmount(BigDecimal.valueOf(total));
            }
        }
        component.add(theoreticalSalaryComponent);
    }
    private void processRevisionSalaryOperation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, ParametersDTO salaryParameters) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get(THEORETICAL_SALARY);
        //log.info("{}", salaryComponent);
        if (salaryComponent != null) {
            ParametersDTO revSalParameters = getParametersById(parameters, 37);
            //log.info("{}", salaryParameters);
            double differPercent = calculateDifferPercent(salaryComponent, salaryParameters);
            //log.info("{}", differPercent);
            double valueRev = calculateValueRev(salaryComponent, revSalParameters);
            //log.info("{}", valueRev);
            updateSalaryComponent(salaryComponent, salaryParameters, differPercent, valueRev);
        }
    }
    private Map<String, PaymentComponentDTO> createComponentMap(List<PaymentComponentDTO> component) {
        return component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> {
                    existing.getProjections().addAll(replacement.getProjections());
                    return existing;
                }));
    }
    private PaymentComponentDTO createTheoreticalSalaryComponent(PaymentComponentDTO pc960400Component, PaymentComponentDTO pc960401Component, String period, Integer range) {
        PaymentComponentDTO theoreticalSalaryComponent = new PaymentComponentDTO();
        theoreticalSalaryComponent.setPaymentComponent(THEORETICAL_SALARY);
        // Calcular el valor de THEORETICAL-SALARY a partir de PC960400 y PC960401
        if (pc960400Component != null && pc960401Component != null) {
            double baseSalary = pc960400Component.getAmount().doubleValue();
            double baseSalaryIntegral = pc960401Component.getAmount().doubleValue();
            theoreticalSalaryComponent.setAmount(BigDecimal.valueOf(Math.max(baseSalary, baseSalaryIntegral)));
            theoreticalSalaryComponent.setProjections(Shared.generateMonthProjection(period, range, theoreticalSalaryComponent.getAmount()));
        }
        return theoreticalSalaryComponent;
    }
    private ParametersDTO getParametersById(List<ParametersDTO> parameters, int id) {
        return parameters.stream()
                .filter(p -> p.getParameter().getId() == id)
                .findFirst()
                .orElse(null);
    }
    private double calculateDifferPercent(PaymentComponentDTO salaryComponent, ParametersDTO salaryParameters) {
        if (salaryParameters == null) {
            return 0.0;
        }
        int idxStart;
        int idxEnd;
        String[] periodRevisionSalary = salaryParameters.getPeriodRetroactive().split("-");
        idxStart = Shared.getIndex(salaryComponent.getProjections().stream()
                .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary[0]);
        idxEnd = Shared.getIndex(salaryComponent.getProjections().stream()
                .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary.length == 1 ? periodRevisionSalary[0] : periodRevisionSalary[1]);
        double salaryFirst = salaryComponent.getProjections().get(idxStart).getAmount().doubleValue();
        double salaryEnd = salaryComponent.getProjections().get(idxEnd).getAmount().doubleValue();
        return (salaryEnd / salaryFirst) - 1;
    }

    private double calculateValueRev(PaymentComponentDTO salaryComponent, ParametersDTO revSalParameters) {
        int idxRev = Shared.getIndex(salaryComponent.getProjections().stream()
                .map(MonthProjection::getMonth).collect(Collectors.toList()), revSalParameters.getPeriod());
        double percentRev2 = revSalParameters.getValue() / 100;
        double amount = salaryComponent.getProjections().get(idxRev).getAmount().doubleValue();
        return amount * percentRev2;
    }
    private void updateSalaryComponent(PaymentComponentDTO salaryComponent, ParametersDTO salaryParameters, double differPercent, double valueRev) {
        double percent = salaryParameters.getValue() / 100;
        int idx = Shared.getIndex(salaryComponent.getProjections().stream()
                .map(MonthProjection::getMonth).collect(Collectors.toList()), salaryParameters.getPeriod());
        if (idx != -1) {
            for (int i = idx; i < salaryComponent.getProjections().size(); i++) {
                double amount = i == 0 ? salaryComponent.getProjections().get(i).getAmount().doubleValue() : salaryComponent.getProjections().get(i - 1).getAmount().doubleValue();
                salaryComponent.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                if (salaryComponent.getProjections().get(i).getMonth().equalsIgnoreCase(salaryParameters.getPeriod())) {
                    if (differPercent > 0) {
                        if (differPercent <= percent) {
                            differPercent = percent - differPercent;
                        } else {
                            differPercent = 0;
                        }
                    } else {
                        differPercent = percent;
                    }
                    double v = (amount * (1 + differPercent)) + valueRev;
                    salaryComponent.getProjections().get(i).setAmount(BigDecimal.valueOf(Math.round(v * 100d) / 100d));
                }
            }
        }
    }
}
