package ms.hispam.budget.rules;

import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.util.Shared;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Ecuador {


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
        List<MonthProjection> months= Shared.generateMonthProjection(period,range,BigDecimal.ZERO);
        months.forEach(f->{
            double[] suma = {0.0};
            componentDTO.stream().filter(c->Arrays.asList(comIess).contains(c.getType())).forEach(d->{
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

    public List<PaymentComponentDTO> fondoReserva(List<PaymentComponentDTO> componentDTO ,String period  ,
                                                   List<ParametersDTO> parameters , Integer range){
        Integer[] comIess ={1,2,3,4,7};
        double amount= 0.0;
        AtomicReference<Double> parameter = new AtomicReference<>((double) 0);
        parameters.stream().filter(c->c.getParameter().getId()==4).findFirst().ifPresent(d-> parameter.set(d.getValue()));

        PaymentComponentDTO newIess = new PaymentComponentDTO();
        newIess.setPaymentComponent("RESERVA");
        for(PaymentComponentDTO ed: componentDTO.stream().
                filter(c->Arrays.asList(comIess).contains(c.getType())).collect(Collectors.toList())){
            amount+=ed.getAmount().doubleValue();

        }
        newIess.setAmount(BigDecimal.valueOf(amount*(parameter.get()/100)));
        List<MonthProjection> months= Shared.generateMonthProjection(period,range,BigDecimal.ZERO);
        months.forEach(f->{
            double[] suma = {0.0};
            componentDTO.stream().filter(c->Arrays.asList(comIess).contains(c.getType())).forEach(d->{
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

    public List<PaymentComponentDTO> decimoTercero(List<PaymentComponentDTO> componentDTO ,String period  ,
                                                    List<ParametersDTO> parameters , Integer range){
        Integer[] comIess ={1,2,3,4,7};
        double amount= 0.0;

        PaymentComponentDTO newIess = new PaymentComponentDTO();
        newIess.setPaymentComponent("DECIMO3");
        for(PaymentComponentDTO ed: componentDTO.stream().
                filter(c->Arrays.asList(comIess).contains(c.getType())).collect(Collectors.toList())){
            amount+=ed.getAmount().doubleValue();

        }
        newIess.setAmount(BigDecimal.valueOf(amount/12));
        List<MonthProjection> months= Shared.generateMonthProjection(period,range,BigDecimal.ZERO);
        months.forEach(f->{
            double[] suma = {0.0};
            componentDTO.stream().filter(c->Arrays.asList(comIess).contains(c.getType())).forEach(d->{
                d.getProjections().stream().filter(g->g.getMonth().equalsIgnoreCase(f.getMonth())).forEach(j->{
                    suma[0] += j.getAmount().doubleValue();
                    f.setAmount(BigDecimal.valueOf(suma[0]));
                });
            });
            f.setAmount(BigDecimal.valueOf(f.getAmount().doubleValue()/12));

        });
        newIess.setProjections(months);
        componentDTO.add(newIess);
        return componentDTO;
    }


    public List<PaymentComponentDTO> addDecimoCuarto(List<PaymentComponentDTO> componentDTO,String period,
                                                      List<ParametersDTO> parameters ,Integer range){
        AtomicReference<Double> amount = new AtomicReference<>(0.0);
        parameters.stream().filter(c->c.getParameter().getId()==5).findFirst().ifPresent(c-> amount.set(c.getValue()));
        PaymentComponentDTO decimo = new PaymentComponentDTO();
        decimo.setPaymentComponent("DECIMO4");
        decimo.setAmount(BigDecimal.valueOf(amount.get() /12));
        decimo.setProjections(Shared.generateMonthProjection(period,range,BigDecimal.valueOf(amount.get()/12)));
        componentDTO.add(decimo);
        return componentDTO;
    }


    public List<PaymentComponentDTO>  adjustSalaryAdjustment(List<PaymentComponentDTO> componentDTO,ParametersDTO dto){
        componentDTO.stream().filter(f->( f.getType()==1|| f.getType()==2 || f.getType()==7) ).forEach(o->{
            // Asegurarse de que las proyecciones estén inicializadas
            if (o.getProjections() == null || o.getProjections().isEmpty()) {
                o.setProjections(Shared.generateMonthProjection(dto.getPeriod(), 12, BigDecimal.ZERO));
            }
            int idx = Shared.getIndex(o.getProjections().stream()
                    .map(d->d.getMonth()).collect(Collectors.toList()),dto.getPeriod());
            for (int i = idx; i < o.getProjections().size(); i++) {
                double amount = i == 0 ? (o.getAmount() != null ? o.getAmount().doubleValue() : 0.0)
                        : o.getProjections().get(i-1).getAmount().doubleValue();
                o.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                if(o.getProjections().get(i).getMonth().equalsIgnoreCase(dto.getPeriod())){
                    AtomicReference<Double> coa = new AtomicReference<>((double) 0);
                    int finalI = i;
                    if(finalI==0){
                        componentDTO.stream().filter(w->w.getType()==2).findFirst()
                                .ifPresent(c-> coa.set(c.getAmount().doubleValue()));
                    }else{
                        componentDTO.stream().filter(w->w.getType()==2).findFirst()
                                .ifPresent(c-> coa.set(c.getProjections().get(finalI - 1).getAmount().doubleValue()));
                    }

                    double v;
                    if(o.getType()==2){
                        AtomicReference<Double> salary = new AtomicReference<>(0.0);
                        componentDTO.stream().filter(c->c.getType()==1).findFirst().ifPresent(k->{
                            salary.set(finalI==0?k.getAmount().doubleValue():k.getProjections().get(finalI-1).getAmount().doubleValue());
                        });
                        v = coa.get() * (1 + ((salary.get() + coa.get()) < dto.getValue() ?
                                (dto.getValue() / (salary.get() + coa.get()) - 1) : 0));
                    }else if(o.getType()==7){
                        AtomicReference<Double> sba = new AtomicReference<>((double) 0);
                        if(finalI==0){
                            componentDTO.stream().filter(w->w.getType()==1).findFirst()
                                    .ifPresent(c-> sba.set(c.getAmount().doubleValue()));
                        }else{
                            componentDTO.stream().filter(w->w.getType()==1).findFirst()
                                    .ifPresent(c-> sba.set(c.getProjections().get(finalI - 1).getAmount().doubleValue()));
                        }

                        v =o.getAmount().doubleValue() * (1 + ((sba.get() + coa.get()) < dto.getValue() ?
                                (dto.getValue() / (sba.get() + coa.get()) - 1) : 0));
                    }else{
                        v = amount * (1 + ((amount + coa.get()) < dto.getValue() ?
                                (dto.getValue() / (amount + coa.get()) - 1) : 0));

                    }
                    o.getProjections().get(i).setAmount(BigDecimal.valueOf(Math.round(v * 100d) / 100d));
                }
            }
        });
        return componentDTO;
    }

    public List<PaymentComponentDTO> srv(List<PaymentComponentDTO> componentDTO, List<ParametersDTO> parameters) {
        // PARAMETROS PARA LA COLUMNA
        AtomicReference<Double> iess = new AtomicReference<>((double) 0);
        AtomicReference<Double> fr = new AtomicReference<>((double) 0);
        parameters.stream().filter(c -> c.getParameter().getId() == 3).findFirst().ifPresent(d -> iess.set(d.getValue()));
        parameters.stream().filter(c -> c.getParameter().getId() == 4).findFirst().ifPresent(d -> fr.set(d.getValue()));
        double percent = (iess.get() + fr.get()) / 100;

        componentDTO.stream()
                .filter(f -> f.getType() != null && f.getType() == 5)
                .forEach(o -> {
                    Double valueDefault = o.getAmount().doubleValue();
                    for (int i = 0; i < o.getProjections().size(); i++) {
                        AtomicReference<Double> sb = new AtomicReference<>(0.0);
                        AtomicReference<Double> co = new AtomicReference<>(0.0);
                        int finalI = i;
                        componentDTO.stream().filter(c -> c.getType() != null && c.getType() == 1).findFirst().ifPresent(f ->
                                sb.set(f.getProjections().get(finalI).getAmount().doubleValue()));
                        componentDTO.stream().filter(c -> c.getType() != null && c.getType() == 2).findFirst().ifPresent(f ->
                                co.set(f.getProjections().get(finalI).getAmount().doubleValue()));
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

        parameters.stream().filter(c -> c.getParameter().getId() == 17).findFirst().ifPresent(d -> parameter.set(d.getValue()));

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
        vaca.setAmount(BigDecimal.valueOf(((salary.get() + coa.get()) * 12) / (24 * 15 * parameter.get())));
        List<MonthProjection> months = Shared.generateMonthProjection(period, range, BigDecimal.ZERO);

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

            f.setAmount(BigDecimal.valueOf(((salaryMonth.get() + coaMonth.get()) * 12) / (24 * 15 * parameter.get())));
        });

        vaca.setProjections(months);
        componentDTO.add(vaca);
        return componentDTO;
    }

    public List<PaymentComponentDTO>  revisionSalary(List<PaymentComponentDTO> componentDTO,ParametersDTO dto  ){
        double differPercent=0.0;
        if(Boolean.TRUE.equals(dto.getIsRetroactive())){
            int idxStart;
            int idxEnd;
            String[]   period;
            period = dto.getPeriodRetroactive().split("-");
            idxStart=  Shared.getIndex(componentDTO.get(1).getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()),period[0]);
            idxEnd=  Shared.getIndex(componentDTO.get(1).getProjections().stream()
                    .map(monthProjection -> monthProjection.getMonth()).collect(Collectors.toList()),period.length==1? period[0]:period[1]);
            AtomicReference<Double> salaryFirst= new AtomicReference<>(0.0);
            AtomicReference<Double> salaryEnd= new AtomicReference<>(0.0);
            AtomicReference<Double> comisionFirst= new AtomicReference<>(0.0);
            AtomicReference<Double> comisionEnd= new AtomicReference<>(0.0);
            componentDTO.stream().filter(c->c.getType()==1).findFirst().ifPresent(l->{
                salaryFirst.set(l.getProjections().get(idxStart).getAmount().doubleValue());
                salaryEnd.set(l.getProjections().get(idxEnd).getAmount().doubleValue());
            });
            componentDTO.stream().filter(c->c.getType()==2).findFirst().ifPresent(l->{
                comisionFirst.set(l.getProjections().get(idxStart).getAmount().doubleValue());
                comisionEnd.set(l.getProjections().get(idxEnd).getAmount().doubleValue());
            });
            differPercent=(salaryEnd.get()+comisionEnd.get())/(salaryFirst.get()+comisionFirst.get())-1;
        }
        double percent = dto.getValue()/100;
        for(PaymentComponentDTO o : componentDTO.stream().filter(f->(
                f.getType()==1|| f.getType()==2 || f.getType()==7)).collect(Collectors.toList())){

            // Asegurarse de que las proyecciones estén inicializadas
            if (o.getProjections() == null || o.getProjections().isEmpty()) {
                o.setProjections(Shared.generateMonthProjection(dto.getPeriod(), 12, BigDecimal.ZERO));
            }

            int idx = Shared.getIndex(o.getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()),dto.getPeriod());
            for (int i = idx; i < o.getProjections().size(); i++) {
                double v=0;
                double amount = i == 0 ? (o.getAmount() != null ? o.getAmount().doubleValue() : 0.0)
                        : o.getProjections().get(i-1).getAmount().doubleValue();
                o.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                if(o.getProjections().get(i).getMonth().equalsIgnoreCase(dto.getPeriod())){
                    if(o.getType()==1 ||o.getType()==7|| o.getType()==2 ){
                        if(o.getType()==7){
                            amount = o.getAmount() != null ? o.getAmount().doubleValue() : 0.0;
                        }
                        v = amount* (1+(differPercent>=percent?0:percent-differPercent));
                    }
                    o.getProjections().get(i).setAmount(BigDecimal.valueOf(Math.round(v * 100d) / 100d));
                }
            }
        }

        return componentDTO;
    }


}
