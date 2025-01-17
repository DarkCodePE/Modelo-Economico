package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.context.UserContext;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.event.SseReportService;
import ms.hispam.budget.service.UserSessionService;
import ms.hispam.budget.util.Shared;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
@Slf4j(topic = "ecuador")
public class Ecuador {

    private final SseReportService sseReportService;
    private final UserSessionService userSessionService;
    private final Map<String, String> sessionCache;

    public Ecuador(SseReportService sseReportService, UserSessionService userSessionService) {
        this.sseReportService = sseReportService;
        this.userSessionService = userSessionService;
        this.sessionCache = new ConcurrentHashMap<>();
    }

    // Método para obtener o crear el ID de sesión
    private String getSessionId(String userId) {
        return sessionCache.computeIfAbsent(userId, userSessionService::createOrUpdateSession);
    }

    public List<PaymentComponentDTO> iess(List<PaymentComponentDTO> componentDTO , String period  ,
                                           List<ParametersDTO> parameters , Integer range){
        Integer[] comIess ={1,2,3,4,7};
        double amount= 0.0;
        AtomicReference<Double> parameter = new AtomicReference<>((double) 0);
        parameters.stream().filter(c->c.getParameter().getId()==3).findFirst().ifPresent(d-> parameter.set(d.getValue()));

        PaymentComponentDTO newIess = new PaymentComponentDTO();
        newIess.setPaymentComponent("IESS");
        for(PaymentComponentDTO ed: componentDTO.stream().
                filter(c-> Arrays.asList(comIess).contains(c.getType())).collect(Collectors.toList())){
            amount+=ed.getAmount().doubleValue();

        }
        newIess.setAmount(BigDecimal.valueOf(amount*(parameter.get()/100)));
        List<MonthProjection> months= Shared.generateMonthProjectionV3(period,range,BigDecimal.ZERO);
        months.forEach(f->{
            double[] suma = {0.0};
            componentDTO
                    .stream()
                    .filter(c->Arrays.asList(comIess).contains(c.getType()))
                    .forEach(d->{
                d.getProjections().stream().filter(g->g.getMonth().equalsIgnoreCase(f.getMonth())).forEach(j->{
                    suma[0] += j.getAmount().doubleValue();
                    f.setAmount(BigDecimal.valueOf(suma[0]));
                });
            });
            f.setAmount(BigDecimal.valueOf(f.getAmount().doubleValue()*(parameter.get()/100.0)));

        });
        newIess.setProjections(months);
        componentDTO.add(newIess);
        return componentDTO;
    }

    public List<PaymentComponentDTO> fondoReserva(List<PaymentComponentDTO> componentDTO, String period,
                                                  List<ParametersDTO> parameters, Integer range) {
        Integer[] comIess = {1, 2, 3, 4, 7};
        double amount = 0.0;
        AtomicReference<Double> parameter = new AtomicReference<>((double) 0);
        parameters.stream().filter(c -> c.getParameter().getId() == 4).findFirst().ifPresent(d -> parameter.set(d.getValue()));

        PaymentComponentDTO newReserva = new PaymentComponentDTO();
        newReserva.setPaymentComponent("RESERVA");

        for (PaymentComponentDTO ed : componentDTO.stream()
                .filter(c -> Arrays.asList(comIess).contains(c.getType()))
                .collect(Collectors.toList())) {
            amount += ed.getAmount().doubleValue();
        }

        // Aplicar el parámetro al monto total anual y luego dividir por 12 para obtener el valor mensual
        newReserva.setAmount(BigDecimal.valueOf(amount * (parameter.get() / 100)));

        List<MonthProjection> months = Shared.generateMonthProjectionV3(period, range, BigDecimal.ZERO);
        months.forEach(f -> {
            double[] suma = {0.0};
            componentDTO.stream().filter(c -> Arrays.asList(comIess).contains(c.getType())).forEach(d -> {
                d.getProjections().stream().filter(g -> g.getMonth().equalsIgnoreCase(f.getMonth())).forEach(j -> {
                    suma[0] += j.getAmount().doubleValue();
                });
            });
            // Aplicar el parámetro y dividir por 12 para obtener el valor mensual correcto
            f.setAmount(BigDecimal.valueOf(suma[0] * (parameter.get() / 100)).setScale(2, BigDecimal.ROUND_HALF_UP));
        });

        newReserva.setProjections(months);
        componentDTO.add(newReserva);
        return componentDTO;
    }

    public List<PaymentComponentDTO> decimoTercero(List<PaymentComponentDTO> componentDTO, String period,
                                                   List<ParametersDTO> parameters, Integer range) {
        Integer[] comIess = {1, 2, 3, 4, 7};
        double amount = 0.0;

        PaymentComponentDTO newDecimo = new PaymentComponentDTO();
        newDecimo.setPaymentComponent("DECIMO3");

        for (PaymentComponentDTO ed : componentDTO.stream()
                .filter(c -> Arrays.asList(comIess).contains(c.getType()))
                .collect(Collectors.toList())) {
            amount += ed.getAmount().doubleValue();
        }

        // Calcular el promedio anual y dividirlo por 12 para obtener el valor mensual
        newDecimo.setAmount(BigDecimal.valueOf(amount / 12));
        //log.info("amount decimoTercero -> {}", amount / 12);
        List<MonthProjection> months = Shared.generateMonthProjectionV3(period, range, BigDecimal.ZERO);
        months.forEach(f -> {
            double[] suma = {0.0};
            componentDTO.stream().filter(c -> Arrays.asList(comIess).contains(c.getType())).forEach(d -> {
                d.getProjections().stream().filter(g -> g.getMonth().equalsIgnoreCase(f.getMonth())).forEach(j -> {
                    suma[0] += j.getAmount().doubleValue();
                    //log.info("suma componentDTO-> {}", j.getAmount().doubleValue());
                });
            });
            //log.info("suma final-> {}", suma[0] / 12);
            // Dividir por 12 para obtener el promedio mensual correcto
            f.setAmount(BigDecimal.valueOf(suma[0] / 12).setScale(2, BigDecimal.ROUND_HALF_UP));
        });

        newDecimo.setProjections(months);
        componentDTO.add(newDecimo);
        return componentDTO;
    }

    public List<PaymentComponentDTO> decimoTerceroV2(List<PaymentComponentDTO> componentDTO, String period,
                                                   List<ParametersDTO> parameters, Integer range) {
        Integer[] comIess = {1, 2, 3, 4, 7};

        PaymentComponentDTO newDecimo = new PaymentComponentDTO();
        newDecimo.setPaymentComponent("DECIMO3");

        List<MonthProjection> months = Shared.generateMonthProjectionV3(period, range, BigDecimal.ZERO);

        months.forEach(currentMonth -> {
            // Calcular la suma mensual solo de los componentes aplicables
            double monthlySum = componentDTO.stream()
                    .filter(c -> c.getType() != null && Arrays.asList(comIess).contains(c.getType()))
                    .flatMap(comp -> comp.getProjections().stream())
                    .filter(proj -> proj.getMonth().equalsIgnoreCase(currentMonth.getMonth()))
                    .mapToDouble(proj -> proj.getAmount() != null ? proj.getAmount().doubleValue() : 0.0)
                    .sum();

            // El décimo tercero es la doceava parte
            double monthlyDecimo = monthlySum / 12.0;

            // Validar que el valor sea mayor a cero
            if (monthlyDecimo >= 0) {
                currentMonth.setAmount(BigDecimal.valueOf(monthlyDecimo)
                        .setScale(2, BigDecimal.ROUND_HALF_UP));
                /*log.info("Décimo tercero calculado para el mes {}: {}",
                        currentMonth.getMonth(), monthlyDecimo);*/
            } else {
                currentMonth.setAmount(BigDecimal.ZERO);
               /* log.warn("Valor inválido de décimo tercero para el mes {}: {}",
                        currentMonth.getMonth(), monthlyDecimo);*/
            }
        });

        // Calcular el monto inicial
        double initialAmount = componentDTO.stream()
                .filter(c -> c.getType() != null && Arrays.asList(comIess).contains(c.getType()))
                .filter(c -> c.getAmount() != null)
                .mapToDouble(c -> c.getAmount().doubleValue())
                .sum() / 12.0;

        newDecimo.setAmount(BigDecimal.valueOf(initialAmount)
                .setScale(2, BigDecimal.ROUND_HALF_UP));
        newDecimo.setProjections(months);
        componentDTO.add(newDecimo);

        return componentDTO;
    }

    public List<PaymentComponentDTO> addDecimoCuarto(List<PaymentComponentDTO> componentDTO,String period,
                                                      List<ParametersDTO> parameters ,Integer range){
        AtomicReference<Double> amount = new AtomicReference<>(0.0);
        parameters.stream().filter(c->c.getParameter().getId()==5).findFirst().ifPresent(c-> amount.set(c.getValue()));
        PaymentComponentDTO decimo = new PaymentComponentDTO();
        decimo.setPaymentComponent("DECIMO4");
        decimo.setAmount(BigDecimal.valueOf(amount.get() /12));
        decimo.setProjections(Shared.generateMonthProjectionV3(period,range,BigDecimal.valueOf(amount.get()/12)));
        componentDTO.add(decimo);
        return componentDTO;
    }


    public List<PaymentComponentDTO> adjustSalaryAdjustment(List<PaymentComponentDTO> componentDTO, ParametersDTO dto) {
        componentDTO.stream()
                .filter(f -> f.getType() != null && (f.getType() == 1 || f.getType() == 2 || f.getType() == 7))
                .forEach(o -> {
                    // Asegurarse de que las proyecciones estén inicializadas
                    if (o.getProjections() == null || o.getProjections().isEmpty()) {
                        o.setProjections(Shared.generateMonthProjectionV3(dto.getPeriod(), 12, BigDecimal.ZERO));
                    }

                    int idx = Shared.getIndex(o.getProjections().stream()
                            .map(MonthProjection::getMonth).collect(Collectors.toList()), dto.getPeriod());

                    for (int i = idx; i < o.getProjections().size(); i++) {
                        double amount = (i == 0) ? (o.getAmount() != null ? o.getAmount().doubleValue() : 0.0)
                                : o.getProjections().get(i - 1).getAmount().doubleValue();

                        o.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));

                        if (o.getProjections().get(i).getMonth().equalsIgnoreCase(dto.getPeriod())) {
                            AtomicReference<Double> coa = new AtomicReference<>(0.0);
                            int finalI = i;

                            // Obtener valor de `coa` dependiendo del índice
                            if (finalI == 0) {
                                componentDTO.stream().filter(w -> w.getType() != null && w.getType() == 2).findFirst()
                                        .ifPresent(c -> coa.set(c.getAmount().doubleValue()));
                            } else {
                                componentDTO.stream().filter(w -> w.getType() != null && w.getType() == 2).findFirst()
                                        .ifPresent(c -> coa.set(c.getProjections().get(finalI - 1).getAmount().doubleValue()));
                            }

                            double v = amount;  // Inicializar `v` con `amount` por defecto

                            // Ajuste basado en el tipo del componente
                            if (o.getType() == 2) {
                                AtomicReference<Double> salary = new AtomicReference<>(0.0);
                                componentDTO.stream().filter(c -> c.getType() != null && c.getType() == 1).findFirst().ifPresent(k -> {
                                    salary.set(finalI == 0 ? k.getAmount().doubleValue() : k.getProjections().get(finalI-1).getAmount().doubleValue());
                                });

                                double base = salary.get() + coa.get();
                                v = (base > 0) ? coa.get() * (1 + ((base < dto.getValue()) ? (dto.getValue() / base - 1) : 0)) : coa.get();

                            } else if (o.getType() == 7) {
                                AtomicReference<Double> sba = new AtomicReference<>(0.0);
                                componentDTO.stream().filter(w -> w.getType() != null && w.getType() == 1).findFirst().ifPresent(c -> {
                                    sba.set(finalI == 0 ? c.getAmount().doubleValue() : c.getProjections().get(finalI - 1).getAmount().doubleValue());
                                });

                                double base = sba.get() + coa.get();
                                v = (base > 0) ? o.getAmount().doubleValue() * (1 + ((base < dto.getValue()) ? (dto.getValue() / base - 1) : 0)) : o.getAmount().doubleValue();

                            } else {
                                double base = amount + coa.get();
                                v = (base > 0) ? amount * (1 + ((base < dto.getValue()) ? (dto.getValue() / base - 1) : 0)) : amount;
                            }

                            // Asignar el valor calculado `v` al mes correspondiente
                            o.getProjections().get(i).setAmount(BigDecimal.valueOf(Math.round(v * 100d) / 100d));
                        }
                    }
                });
        return componentDTO;
    }

    public List<PaymentComponentDTO> srv(List<PaymentComponentDTO> componentDTO, List<ParametersDTO> parameters) {
        AtomicReference<Double> iess = new AtomicReference<>((double) 0);
        AtomicReference<Double> fr = new AtomicReference<>((double) 0);
        parameters.stream().filter(c -> c.getParameter().getId() == 3).findFirst().ifPresent(d -> iess.set(d.getValue()));
        parameters.stream().filter(c -> c.getParameter().getId() == 4).findFirst().ifPresent(d -> fr.set(d.getValue()));
        double percent = (iess.get() + fr.get()) / 100;

        componentDTO.stream()
                .filter(f -> f.getType() != null && f.getType() == 5)
                .forEach(o -> {
                    Double valueDefault = o.getAmount().doubleValue();
                    // Usamos el tamaño de las proyecciones del componente actual
                    int projectionSize = o.getProjections().size();
                    for (int i = 0; i < projectionSize; i++) {
                        AtomicReference<Double> sb = new AtomicReference<>(0.0);
                        AtomicReference<Double> co = new AtomicReference<>(0.0);
                        int finalI = i;

                        // Aseguramos que no excedemos los límites de las proyecciones
                        componentDTO.stream()
                                .filter(c -> c.getType() != null && c.getType() == 1)
                                .findFirst()
                                .ifPresent(f -> {
                                    if (finalI < f.getProjections().size()) {
                                        sb.set(f.getProjections().get(finalI).getAmount().doubleValue());
                                    }
                                });

                        componentDTO.stream()
                                .filter(c -> c.getType() != null && c.getType() == 2)
                                .findFirst()
                                .ifPresent(f -> {
                                    if (finalI < f.getProjections().size()) {
                                        co.set(f.getProjections().get(finalI).getAmount().doubleValue());
                                    }
                                });

                        double v = (((sb.get() + co.get()) * 13 * valueDefault / 100) *
                                (1 + (1.0 / 12.0) + percent)) / 12;
                        o.getProjections().get(i).setAmount(BigDecimal.valueOf(Math.round(v * 100d) / 100d));
                        if (i == 0) {
                            o.setAmount(o.getProjections().get(i).getAmount());
                        }
                    }
                });
        return componentDTO;
    }

    public List<PaymentComponentDTO> vacations(List<PaymentComponentDTO> componentDTO, String period,
                                               List<ParametersDTO> parameters, Integer range) {
        AtomicReference<Double> parameter = new AtomicReference<>((double) 0);
        AtomicReference<Double> salary = new AtomicReference<>((double) 0);
        AtomicReference<Double> coa = new AtomicReference<>((double) 0);

        parameters.stream()
                .filter(c -> c.getParameter().getId() == 17)
                .findFirst()
                .ifPresent(d -> parameter.set(d.getValue()));

        componentDTO.stream()
                .filter(c -> c.getType() != null && c.getType() == 1)
                .findFirst()
                .ifPresent(k -> salary.set(k.getAmount().doubleValue()));

        componentDTO.stream()
                .filter(c -> c.getType() != null && c.getType() == 2)
                .findFirst()
                .ifPresent(k -> coa.set(k.getAmount().doubleValue()));

        PaymentComponentDTO vaca = new PaymentComponentDTO();
        vaca.setPaymentComponent("VACA");
        // Calcular el denominador condicionalmente
        double denominator = (parameter.get() != 0) ? (24 * 15 * parameter.get()) : 1;
        // Calcular el monto de vacaciones
        double vacacionesMonto = ((salary.get() + coa.get()) * 12) / denominator;
        vaca.setAmount(BigDecimal.valueOf(vacacionesMonto / denominator));
        List<MonthProjection> months = Shared.generateMonthProjectionV3(period, range, BigDecimal.ZERO);

        months.forEach(f -> {
            AtomicReference<Double> salaryMonth = new AtomicReference<>((double) 0);
            AtomicReference<Double> coaMonth = new AtomicReference<>((double) 0);

            componentDTO.stream()
                    .filter(c -> c.getType() != null && c.getType() == 1)
                    .findFirst()
                    .flatMap(k -> k.getProjections().stream()
                            .filter(y -> y.getMonth().equalsIgnoreCase(f.getMonth()))
                            .findFirst())
                    .ifPresent(u -> salaryMonth.set(u.getAmount().doubleValue()));

            componentDTO.stream()
                    .filter(c -> c.getType() != null && c.getType() == 2)
                    .findFirst()
                    .flatMap(k -> k.getProjections().stream()
                            .filter(y -> y.getMonth().equalsIgnoreCase(f.getMonth()))
                            .findFirst())
                    .ifPresent(u -> coaMonth.set(u.getAmount().doubleValue()));
            // Calcular el denominador condicionalmente
            double denominatorMonth = (parameter.get() != 0) ? (24 * 15 * parameter.get()) : 1;
            f.setAmount(BigDecimal.valueOf(((salaryMonth.get() + coaMonth.get()) * 12) / denominatorMonth));
        });

        vaca.setProjections(months);
        componentDTO.add(vaca);
        return componentDTO;
    }

    public List<PaymentComponentDTO> revisionSalary(List<PaymentComponentDTO> componentDTO, ParametersDTO dto) {
        try {
            double differPercent = 0.0;
            if (Boolean.TRUE.equals(dto.getIsRetroactive()) &&
                    dto.getPeriodRetroactive() != null &&
                    !dto.getPeriodRetroactive().trim().isEmpty()) {
                int idxStart;
                int idxEnd;
                String[] period;
                period = dto.getPeriodRetroactive().split("-");
                idxStart = Shared.getIndex(componentDTO.get(1).getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()), period[0]);
                idxEnd = Shared.getIndex(componentDTO.get(1).getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()), period.length == 1 ? period[0] : period[1]);
                AtomicReference<Double> salaryFirst = new AtomicReference<>(0.0);
                AtomicReference<Double> salaryEnd = new AtomicReference<>(0.0);
                AtomicReference<Double> comisionFirst = new AtomicReference<>(0.0);
                AtomicReference<Double> comisionEnd = new AtomicReference<>(0.0);
                componentDTO.stream().filter(c -> c.getType() != null && c.getType() == 1).findFirst().ifPresent(l -> {
                    salaryFirst.set(l.getProjections().get(idxStart).getAmount().doubleValue());
                    salaryEnd.set(l.getProjections().get(idxEnd).getAmount().doubleValue());
                });
                componentDTO.stream().filter(c -> c.getType() != null && c.getType() == 2).findFirst().ifPresent(l -> {
                    comisionFirst.set(l.getProjections().get(idxStart).getAmount().doubleValue());
                    comisionEnd.set(l.getProjections().get(idxEnd).getAmount().doubleValue());
                });
                double baseAmount = salaryFirst.get() + comisionFirst.get();
                if (baseAmount > 0) {
                    differPercent = ((salaryEnd.get() + comisionEnd.get()) / baseAmount) - 1;
                } else {
                    differPercent = 0.0;
                }
                //differPercent = (salaryEnd.get() + comisionEnd.get()) / (salaryFirst.get() + comisionFirst.get()) - 1;
            }
            double percent = dto.getValue() / 100;
            for (PaymentComponentDTO o : componentDTO.stream()
                    .filter(f -> f.getType() != null && (f.getType() == 1 || f.getType() == 2 || f.getType() == 7))
                    .collect(Collectors.toList())) {

                // Asegurarse de que las proyecciones estén inicializadas
                if (o.getProjections() == null || o.getProjections().isEmpty()) {
                    o.setProjections(Shared.generateMonthProjectionV3(dto.getPeriod(), 12, BigDecimal.ZERO));
                }

                int idx = Shared.getIndex(o.getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()), dto.getPeriod());
                for (int i = idx; i < o.getProjections().size(); i++) {
                    double v = 0;
                    double amount = i == 0 ? (o.getAmount() != null ? o.getAmount().doubleValue() : 0.0)
                            : o.getProjections().get(i - 1).getAmount().doubleValue();
                    o.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                    if (o.getProjections().get(i).getMonth().equalsIgnoreCase(dto.getPeriod())) {
                        if (o.getType() == 1 || o.getType() == 7 || o.getType() == 2) {
                            if (o.getType() == 7) {
                                amount = o.getAmount() != null ? o.getAmount().doubleValue() : 0.0;
                            }
                            // Protección contra NaN y valores negativos
                            double adjustmentFactor = Math.max(0, differPercent >= percent ? 0 : percent - differPercent);
                            //log.info("adjustmentFactor -> {}", adjustmentFactor);
                            //log.info("amount -> {}", amount);
                            v = amount * (1 + adjustmentFactor);
                            //log.info("v -> {}", v);
                        }
                        o.getProjections().get(i).setAmount(BigDecimal.valueOf(Math.round(v * 100d) / 100d));
                    }
                }
            }
            return componentDTO;
        } catch (Exception e) {
            String userId = UserContext.getCurrentUser();
            //sseReportService.sendUpdate(userId, "error", "Error en la revisión salarial: " + e.getMessage());
            throw new RuntimeException("Error en la revisión salarial", e);
        }
    }


}
