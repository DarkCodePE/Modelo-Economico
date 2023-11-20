package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.cache.DateCache;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.ParamFilterDTO;
import ms.hispam.budget.service.MexicoService;
import ms.hispam.budget.util.DaysVacationInfo;
import ms.hispam.budget.util.Shared;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "Mexico")
public class Mexico {
    private final MexicoService mexicoService;
    static final String TYPEMONTH="yyyyMM";
    private DateCache dateCache = new DateCache(3);
    private static final List<DaysVacationInfo> daysVacationList = Arrays.asList(
            new DaysVacationInfo(1, 1, 12),
            new DaysVacationInfo(2, 2, 14),
            new DaysVacationInfo(3, 3, 16),
            new DaysVacationInfo(4, 4, 19),
            new DaysVacationInfo(9, 9, 22),
            new DaysVacationInfo(14, 14, 24),
            new DaysVacationInfo(19, 19, 26),
            new DaysVacationInfo(24, 24, 28),
            new DaysVacationInfo(30, 30, 30),
            new DaysVacationInfo(35, 35, 32)
    );

    private Map<String, Integer> vacationsDaysCache = new ConcurrentHashMap<>();
    @Autowired
    public Mexico(MexicoService mexicoService) {
        this.mexicoService = mexicoService;
    }

    public List<RangeBuDetailDTO> getAllDaysVacation(List<RangeBuDetailDTO> rangeBu, Integer idBu) {
        return mexicoService.getAllDaysVacation(rangeBu, idBu);
    }

    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
       //SI(ESNUMERO(HALLAR("CP";$H4));
        // HEADCOUNT: OBTIENS LAS PROYECCION Y LA PO, AQUI AGREGAMOS LOS PARAMETROS CUSTOM
        double baseSalary = 0;
        double baseSalaryIntegral = 0;
        //TRAER COMPONENTES DE PAGO PARA CALCULAR EL SALARIO BASE PC938001 - PC938005
        for (PaymentComponentDTO p : component) {
            if (p.getPaymentComponent().equalsIgnoreCase("PC320001")) {
                baseSalary = p.getAmount().doubleValue();
            } else if (p.getPaymentComponent().equalsIgnoreCase("PC320002")) {
                baseSalaryIntegral = p.getAmount().doubleValue();
            }
        }
        PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
        paymentComponentDTO.setPaymentComponent("SALARY");
        paymentComponentDTO.setAmount(BigDecimal.valueOf(Math.max(baseSalary, baseSalaryIntegral)));
        paymentComponentDTO.setProjections(Shared.generateMonthProjection(period, range, paymentComponentDTO.getAmount()));

        double incrementSalaryPercent = 0;
        for (ParametersDTO param : parameters) {
            if (param.getParameter().getId() == 32) {
                incrementSalaryPercent = param.getValue();
            }
        }
        //List min salaries
        List<ParametersDTO> salaryList = parameters.stream()
                .filter(p -> Objects.equals(p.getParameter().getName(), "Salario Mínimo Mexico"))
                .collect(Collectors.toList());

        double percent = incrementSalaryPercent / 100;
        double paramValueSalary;
        boolean currentSalaryParamStatus;
        for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
            double amount = i == 0 ? paymentComponentDTO.getProjections().get(i).getAmount().doubleValue() : paymentComponentDTO.getProjections().get(i - 1).getAmount().doubleValue();
            if (salaryList.isEmpty()) {
                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                continue;
            }
            String currentMonthProjection = paymentComponentDTO.getProjections().get(i).getMonth();
            ParamFilterDTO currentSalaryParamByMonth = getValueSalaryByPeriod(salaryList, currentMonthProjection);
            currentSalaryParamStatus = currentSalaryParamByMonth.getStatus();

            if (Boolean.TRUE.equals(currentSalaryParamStatus)) {
                paramValueSalary = currentSalaryParamByMonth.getValue();
                DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                        .appendPattern(TYPEMONTH)
                        .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                        .toFormatter();
                LocalDate dateProjection = LocalDate.parse(currentMonthProjection, dateFormat);
                this.dateCache.put(dateProjection, paramValueSalary);
                if (amount < paramValueSalary && paramValueSalary != 0.0) {
                    double incrementSalary = amount * percent;
                    paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount + incrementSalary));
                } else {
                    paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                }
            } else {
                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
            }
        }
        component.add(paymentComponentDTO);
    }
    public void revisionSalary(List<PaymentComponentDTO> component,List<ParametersDTO> parameters){
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .parallel()
                .forEach(paymentComponentDTO -> {
                    //boolean isApplyPercent=false;
                    AtomicReference<String> periodSalaryMin = new AtomicReference<>((String) "");
                    AtomicReference<Boolean> isRetroactive = new AtomicReference<>((Boolean) false);
                    AtomicReference<String[]> periodRetractive = new AtomicReference<>((String[]) null);
                    AtomicReference<Double> percetange = new AtomicReference<>((double) 0);
                    parameters.stream()
                            .filter(p -> p.getParameter().getId() == 1)
                            .findFirst()
                            .ifPresent(param -> {
                                periodSalaryMin.set(param.getPeriod());
                                isRetroactive.set(param.getIsRetroactive());
                                periodRetractive.set(param.getPeriodRetroactive().split("-"));
                                percetange.set(param.getValue());
                            });
                   if (periodRetractive.get() != null ) {
                       //List min salaries
                       String[] periodRevisionSalary = periodRetractive.get();
                       double salaryEnd = this.dateCache.getUltimoValorEnRango(periodRevisionSalary[0], periodRevisionSalary[1]);
                       double differPercent=0.0;
                       if(Boolean.TRUE.equals(isRetroactive.get())){
                           int idxStart;
                           idxStart=  Shared.getIndex(paymentComponentDTO.getProjections().stream()
                                   .map(MonthProjection::getMonth).collect(Collectors.toList()),periodRevisionSalary[0]);
                           AtomicReference<Double> salaryFirst= new AtomicReference<>(0.0);
                           salaryFirst.set(paymentComponentDTO.getProjections().get(idxStart).getAmount().doubleValue());
                           //isApplyPercent = salaryFirst.get() < salaryEnd;
                           differPercent=(salaryFirst.get()/(salaryEnd))-1;
                       }
                       double percent = percetange.get()/100;
                       if(differPercent > 0 && differPercent <= percent){
                           differPercent = percent - differPercent;
                       }else {
                           differPercent = percent;
                       }
                       int idxRevSal = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                               .map(MonthProjection::getMonth).collect(Collectors.toList()),periodSalaryMin.get());
                       if (idxRevSal != -1){
                           for (int i = idxRevSal; i < paymentComponentDTO.getProjections().size(); i++) {
                               double v;
                               double amount = i==0?paymentComponentDTO.getProjections().get(i).getAmount().doubleValue(): paymentComponentDTO.getProjections().get(i-1).getAmount().doubleValue();
                               //R11*(1+SI(P11<Q$2;0;S$4))
                               if(paymentComponentDTO.getProjections().get(i).getMonth().equalsIgnoreCase(periodSalaryMin.get())){
                                   v = amount* (1+(differPercent));
                               }else {
                                   v = amount;
                               }
                               /* paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(isApplyPercent ? amount * (1 + percent) : amount));*/
                               paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(v));
                               //differPercent = 0.0;
                           }
                       }
                   }
                });
    }
    public ParamFilterDTO getValueSalaryByPeriod(List<ParametersDTO> params, String periodProjection){
        //ordenar lista
        sortParameters(params);
        AtomicReference<Double> value = new AtomicReference<>(0.0);
        AtomicReference<String> periodSalaryMin = new AtomicReference<>("");
        Boolean status = params.stream()
                .anyMatch(p -> {
                    DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                            .appendPattern(TYPEMONTH)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter();
                    LocalDate dateParam = LocalDate.parse(p.getPeriod(),dateFormat);
                    LocalDate dateProjection = LocalDate.parse(periodProjection, dateFormat);
                    value.set(p.getValue());
                    periodSalaryMin.set(p.getPeriod());
                    return dateParam.equals(dateProjection);
                });
        //TODO ELEMINAR EL ELEMENTO SELECCIONADO DE LISTA
        return ParamFilterDTO.builder()
                .status(status)
                .value(value.get())
                .period(periodSalaryMin.get())
                .build();
    }
    private void sortParameters(List<ParametersDTO> params) {
        params.sort((o1, o2) -> {
            DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                    .appendPattern(TYPEMONTH)
                    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                    .toFormatter();
            LocalDate dateParam = LocalDate.parse(o1.getPeriod(),dateFormat);
            LocalDate dateProjection = LocalDate.parse(o2.getPeriod(), dateFormat);
            return dateParam.compareTo(dateProjection);
        });
    }
    public void provAguinaldo(List<PaymentComponentDTO> component, String period, Integer range) {
        // get salary
        PaymentComponentDTO paymentComponentDTO = component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .findFirst()
                .orElse(null);
        // create payment component prov aguinaldo
        if (paymentComponentDTO != null){
            //PaymentComponentDTO paymentComponentDTOClone = paymentComponentDTO.clone();
            PaymentComponentDTO paymentComponentProvAguin = new PaymentComponentDTO(
                    paymentComponentDTO.getPaymentComponent(),
                    paymentComponentDTO.getAmount(),
                    paymentComponentDTO.getProjections()
            ).createWithProjections();
            paymentComponentProvAguin.setPaymentComponent("AGUINALDO");
            BigDecimal aguinaldoAmount = BigDecimal.valueOf((paymentComponentDTO.getAmount().doubleValue() / 30) * 1.25);
            paymentComponentProvAguin.setAmount(aguinaldoAmount);
            //paymentComponentProvAguin.setProjections(paymentComponentDTOClone.getProjections());
            // prov aguinaldo
           /* for (int i = 0; i < paymentComponentProvAguin.getProjections().size(); i++) {
                double amountProj = paymentComponentProvAguin.getProjections().get(i).getAmount().doubleValue();
                paymentComponentProvAguin.getProjections().get(i).setAmount(BigDecimal.valueOf((amountProj/30) * 1.25));
            }*/
            for (MonthProjection projection : paymentComponentProvAguin.getProjections()) {
                double amountProj = projection.getAmount().doubleValue();
                BigDecimal newAmount = BigDecimal.valueOf((amountProj / 30) * 1.25);
                projection.setAmount(newAmount);
            }
            component.add(paymentComponentProvAguin);
        }
    }
    private boolean isValidRange(long seniority, String range) {
        if (range == null || range.isEmpty()) {
            return false;
        }
        String[] parts = range.split("a");
        if (parts.length == 1) {
            // Si solo hay un valor, verificar si coincide con la antigüedad
            try {
                long singleValue = Long.parseLong(parts[0].trim());
                return seniority == singleValue;
            } catch (NumberFormatException e) {
                // Manejar la excepción si no se puede convertir a número
                return false;
            }
        } else if (parts.length == 2) {
            // Si hay dos valores, verificar si la antigüedad está en el rango
            try {
                long startRange = Long.parseLong(parts[0].trim());
                long endRange = Long.parseLong(parts[1].trim());
                return seniority >= startRange && seniority <= endRange;
            } catch (NumberFormatException e) {
                // Manejar la excepción si no se pueden convertir a números
                return false;
            }
        }
        // Si no hay uno o dos valores, el formato no es válido
        return false;
    }

    public void provVacacionesRefactor(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, LocalDate dateContract, LocalDate dateBirth, List<RangeBuDTO> rangeBu, Integer idBu) {
        Objects.requireNonNull(dateContract, "La fecha de contrato no puede ser nula");

        LocalDate dateActual = LocalDate.now();
        long seniority = Math.max(ChronoUnit.YEARS.between(dateContract, dateActual), 0);
        //log.info("seniority: {}",seniority);
        RangeBuDTO rangeBuByBU = rangeBu.stream()
                .filter(r -> r.getIdBu().equals(idBu))
                .findFirst()
                .orElse(null);
        //log.info("rangeBuByBU: {}",rangeBuByBU);
        List<RangeBuDetailDTO> daysVacations;

        if (rangeBuByBU != null) {
             daysVacations = getAllDaysVacation(rangeBuByBU.getRangeBuDetails(), idBu);
        }else {
            daysVacations = getDaysVacationList();
        }
        if (daysVacations.isEmpty()) daysVacations = getDaysVacationList();
        Map<String, RangeBuDetailDTO> daysVacationsMap = daysVacations.stream()
                .collect(Collectors.toMap(RangeBuDetailDTO::getRange, Function.identity()));

        int vacationsDays = getCachedVacationsDays(seniority, daysVacationsMap);

        if (vacationsDays == 0) {
            int daysVacationPerMonth = daysVacationList.get(0).getVacationDays() / 12;
            long monthsSeniority = ChronoUnit.MONTHS.between(dateContract, dateActual);
            vacationsDays = (int) (daysVacationPerMonth * monthsSeniority);
        }

        PaymentComponentDTO paymentComponentDTO = component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .findFirst()
                .orElse(null);

        if (paymentComponentDTO != null) {
            PaymentComponentDTO paymentComponentProvVacations = new PaymentComponentDTO(
                    paymentComponentDTO.getPaymentComponent(),
                    paymentComponentDTO.getAmount(),
                    paymentComponentDTO.getProjections()
            ).createWithProjections();
            paymentComponentProvVacations.setPaymentComponent("VACACIONES");
            BigDecimal vacationAmount = BigDecimal.valueOf((paymentComponentDTO.getAmount().doubleValue() / 30) * vacationsDays / 12);
            paymentComponentProvVacations.setAmount(vacationAmount);

            for (MonthProjection projection : paymentComponentProvVacations.getProjections()) {
                double amountProj = projection.getAmount().doubleValue() / 30;
                BigDecimal newAmount = BigDecimal.valueOf((amountProj * vacationsDays) / 12);
                projection.setAmount(newAmount);
            }

            component.add(paymentComponentProvVacations);
        }
    }
    //TODO: REFACTOR FALLBACK --> URGENTE
    private List<RangeBuDetailDTO> getDaysVacationList() {
        // Puedes inicializar tu lista aquí con los valores proporcionados
        return Arrays.asList(
                new RangeBuDetailDTO(1, "1", 10, 12),
                new RangeBuDetailDTO(1, "2",11, 14),
                new RangeBuDetailDTO(1, "3", 12,16),
                new RangeBuDetailDTO(1, "4", 13,19),
                new RangeBuDetailDTO(1, "5 a 9",14, 22)
        );
    }
    private int getCachedVacationsDays(long seniority, Map<String, RangeBuDetailDTO> daysVacationsMap) {
        String seniorityKey = String.valueOf(seniority);
        return vacationsDaysCache.computeIfAbsent(seniorityKey, key -> {
            RangeBuDetailDTO daysVacation = daysVacationsMap.get(key);
            if (daysVacation != null) {
                return daysVacation.getValue();
            }
            return 0;
        });
    }

    private boolean isValidRange(long seniority, DaysVacationInfo daysVacation) {
        return seniority >= daysVacation.getLowerLimit() && seniority <= daysVacation.getUpperLimit();
    }
}
