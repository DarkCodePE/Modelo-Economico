package ms.hispam.budget.rules;

import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.util.Shared;

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
            amount+=ed.getAmount();

        }
        newIess.setAmount(amount*(parameter.get()/100));
        List<MonthProjection> months= Shared.generateMonthProjection(period,range,0.0);
        months.forEach(f->{
            double[] suma = {0.0};
            componentDTO.stream().filter(c->Arrays.asList(comIess).contains(c.getType())).forEach(d->{
                d.getProjections().stream().filter(g->g.getMonth().equalsIgnoreCase(f.getMonth())).forEach(j->{
                    suma[0] += j.getAmount();
                    f.setAmount(suma[0]);
                });
            });
            f.setAmount(f.getAmount()*(parameter.get()/100.0));

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
            amount+=ed.getAmount();

        }
        newIess.setAmount(amount*(parameter.get()/100));
        List<MonthProjection> months= Shared.generateMonthProjection(period,range,0.0);
        months.forEach(f->{
            double[] suma = {0.0};
            componentDTO.stream().filter(c->Arrays.asList(comIess).contains(c.getType())).forEach(d->{
                d.getProjections().stream().filter(g->g.getMonth().equalsIgnoreCase(f.getMonth())).forEach(j->{
                    suma[0] += j.getAmount();
                    f.setAmount(suma[0]);
                });
            });
            f.setAmount(f.getAmount()*(parameter.get()/100.0));

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
            amount+=ed.getAmount();

        }
        newIess.setAmount(amount/12);
        List<MonthProjection> months= Shared.generateMonthProjection(period,range,0.0);
        months.forEach(f->{
            double[] suma = {0.0};
            componentDTO.stream().filter(c->Arrays.asList(comIess).contains(c.getType())).forEach(d->{
                d.getProjections().stream().filter(g->g.getMonth().equalsIgnoreCase(f.getMonth())).forEach(j->{
                    suma[0] += j.getAmount();
                    f.setAmount(suma[0]);
                });
            });
            f.setAmount(f.getAmount()/12);

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
        decimo.setAmount(amount.get()/12);
        decimo.setProjections(Shared.generateMonthProjection(period,range,amount.get()/12));
        componentDTO.add(decimo);
        return componentDTO;
    }

    public List<PaymentComponentDTO>  adjustSalaryAdjustment(List<PaymentComponentDTO> componentDTO,ParametersDTO dto){
        componentDTO.stream().filter(f->( f.getType()==1|| f.getType()==2 || f.getType()==7) ).forEach(o->{
            int idx = Shared.getIndex(o.getProjections().stream()
                    .map(d->d.getMonth()).collect(Collectors.toList()),dto.getPeriod());
            for (int i = idx; i < o.getProjections().size(); i++) {
                double amount = i==0?o.getProjections().get(i).getAmount(): o.getProjections().get(i-1).getAmount();
                o.getProjections().get(i).setAmount(amount);
                if(o.getProjections().get(i).getMonth().equalsIgnoreCase(dto.getPeriod())){
                    AtomicReference<Double> coa = new AtomicReference<>((double) 0);
                    int finalI = i;
                    if(finalI==0){
                        componentDTO.stream().filter(w->w.getType()==2).findFirst()
                                .ifPresent(c-> coa.set(c.getAmount()));
                    }else{
                        componentDTO.stream().filter(w->w.getType()==2).findFirst()
                                .ifPresent(c-> coa.set(c.getProjections().get(finalI - 1).getAmount()));
                    }

                    double v;
                    if(o.getType()==2){
                        AtomicReference<Double> salary = new AtomicReference<>(0.0);
                        componentDTO.stream().filter(c->c.getType()==1).findFirst().ifPresent(k->{
                            salary.set(finalI==0?k.getAmount():k.getProjections().get(finalI-1).getAmount());
                        });
                        v = coa.get() * (1 + ((salary.get() + coa.get()) < dto.getValue() ?
                                (dto.getValue() / (salary.get() + coa.get()) - 1) : 0));
                    }else if(o.getType()==7){
                        AtomicReference<Double> sba = new AtomicReference<>((double) 0);
                        if(finalI==0){
                            componentDTO.stream().filter(w->w.getType()==1).findFirst()
                                    .ifPresent(c-> sba.set(c.getAmount()));
                        }else{
                            componentDTO.stream().filter(w->w.getType()==1).findFirst()
                                    .ifPresent(c-> sba.set(c.getProjections().get(finalI - 1).getAmount()));
                        }

                        v =o.getAmount() * (1 + ((sba.get() + coa.get()) < dto.getValue() ?
                                (dto.getValue() / (sba.get() + coa.get()) - 1) : 0));
                    }else{
                        v = amount * (1 + ((amount + coa.get()) < dto.getValue() ?
                                (dto.getValue() / (amount + coa.get()) - 1) : 0));

                    }
                    o.getProjections().get(i).setAmount(Math.round(v * 100d) / 100d);
                }
            }
        });
        return componentDTO;
    }

    public List<PaymentComponentDTO> srv(List<PaymentComponentDTO> componentDTO, List<ParametersDTO> parameters   ){
        AtomicReference<Double> iess = new AtomicReference<>((double) 0);
        AtomicReference<Double> fr = new AtomicReference<>((double) 0);
        parameters.stream().filter(c->c.getParameter().getId()==3).findFirst().ifPresent(d-> iess.set(d.getValue()));
        parameters.stream().filter(c->c.getParameter().getId()==4).findFirst().ifPresent(d-> fr.set(d.getValue()));
        double percent=(iess.get()+fr.get())/100;
        componentDTO.stream().filter(f-> f.getType()==5 ).forEach(o->{
            Double valueDefault =o.getAmount();
            for (int i = 0; i < o.getProjections().size(); i++) {
                AtomicReference<Double> sb= new AtomicReference<>(0.0);
                AtomicReference<Double> co= new AtomicReference<>(0.0);
                int finalI = i;
                componentDTO.stream().filter(c->c.getType()==1).findFirst().ifPresent(f->
                        sb.set(f.getProjections().get(finalI).getAmount()));
                componentDTO.stream().filter(c->c.getType()==2).findFirst().ifPresent(f->
                        co.set(f.getProjections().get(finalI).getAmount()));
                double v = (((sb.get()+co.get())*13*valueDefault/100) *
                        (1+(1.0/12.0)+percent))/12;
                o.getProjections().get(i).setAmount(Math.round(v * 100d) / 100d);
                if(i==0){
                    o.setAmount(o.getProjections().get(i).getAmount());
                }

            }
        });
        return componentDTO;
    }

    public List<PaymentComponentDTO>  revisionSalary(List<PaymentComponentDTO> componentDTO,ParametersDTO dto ,
                                                      List<ParametersDTO> parameters ){
        int idxStart;
        int idxEnd=0;
        String[]   period;
        if(dto.getIsRetroactive()){
            period = dto.getPeriodRetroactive().split("-");
            idxStart=  Shared.getIndex(componentDTO.get(1).getProjections().stream()
                    .map(d->d.getMonth()).collect(Collectors.toList()),period[0]);
            idxEnd=  Shared.getIndex(componentDTO.get(1).getProjections().stream()
                    .map(d->d.getMonth()).collect(Collectors.toList()),period.length==1? period[0]:period[1]);
        } else {
            idxStart = 0;
            period = null;
        }
        int cantRetroactive=!dto.getIsRetroactive()?1:  (idxEnd-idxStart)+2;
        double percent = dto.getValue()/100;

        AtomicReference<Double> asm = new AtomicReference<>(0.0);
        if(period!=null){
            parameters.stream().filter(p->p.getType()!=null).filter(p->(p.getType()==1 || p.getType()==2) && p.getPeriod().equalsIgnoreCase(period[0]) )
                    .findFirst().ifPresent(p-> asm.set(p.getValue()));
        }
        for(PaymentComponentDTO o : componentDTO.stream().filter(f->(
                f.getType()==1|| f.getType()==2 || f.getType()==7)).collect(Collectors.toList())){

            AtomicReference<Double> salaryMonthBeforeStart= new AtomicReference<>(0.0);
            AtomicReference<Double> salaryMonthStart= new AtomicReference<>(o.getProjections().get(idxStart).getAmount());
            AtomicReference<Double> comisionMonthBeforeStart= new AtomicReference<>(0.0);
            AtomicReference<Double> comisionMonthStart= new AtomicReference<>(o.getProjections().get(idxStart).getAmount());
            componentDTO.stream().filter(c->c.getType()==1).findFirst().ifPresent(l->{
                salaryMonthBeforeStart.set(idxStart ==0?l.getAmount():l.getProjections().get(idxStart-1).getAmount());
                salaryMonthStart.set(l.getProjections().get(idxStart).getAmount());
            });
            componentDTO.stream().filter(c->c.getType()==2).findFirst().ifPresent(l->{
                comisionMonthBeforeStart.set(idxStart ==0?l.getAmount():l.getProjections().get(idxStart-1).getAmount());
                comisionMonthStart.set(l.getProjections().get(idxStart).getAmount());
            });
            int idx = Shared.getIndex(o.getProjections().stream()
                    .map(d->d.getMonth()).collect(Collectors.toList()),dto.getPeriod());
            for (int i = idx; i < o.getProjections().size(); i++) {
                AtomicReference<Double> coa = new AtomicReference<>((double) 0);
                int finalI = i;
                //Obtener comision del mes anterior
                if(finalI==0){
                    componentDTO.stream().filter(w->w.getType()==2).findFirst()
                            .ifPresent(c-> coa.set(c.getAmount()));
                }else{
                    componentDTO.stream().filter(w->w.getType()==2).findFirst()
                            .ifPresent(c-> coa.set(c.getProjections().get(finalI - 1).getAmount()));
                }

                double amount = i==0?o.getProjections().get(i).getAmount(): o.getProjections().get(i-1).getAmount();
                o.getProjections().get(i).setAmount(amount);
                if(i == idx+1){
                    double  afterRevision=0.0;
                    if(i==1){
                        afterRevision= o.getAmount();
                    }else{
                        afterRevision= o.getProjections().get(i-2).getAmount();
                    }

                    double v =afterRevision*(1+ ((salaryMonthBeforeStart.get().floatValue()!=salaryMonthStart.get().floatValue())?
                            (asm.get()/(salaryMonthBeforeStart.get()+comisionMonthBeforeStart.get())-1< percent)?
                                    percent-(asm.get()/(salaryMonthBeforeStart.get()+comisionMonthBeforeStart.get())-1):0 :percent));
                    o.getProjections().get(i).setAmount(Math.round(v * 100d) / 100d);
                }
                if(o.getProjections().get(i).getMonth().equalsIgnoreCase(dto.getPeriod())){
                    double v=0;
                    if(o.getType()==1 ||o.getType()==7 ){
                        if(o.getType()==7){
                            amount=o.getAmount();
                        }
                        v = amount +(cantRetroactive* amount*
                                (salaryMonthBeforeStart.get().floatValue()!=salaryMonthStart.get().floatValue()?
                                        (asm.get()/(salaryMonthBeforeStart.get()+ comisionMonthBeforeStart.get())-1<percent?
                                                percent-(asm.get()/(salaryMonthBeforeStart.get()+comisionMonthBeforeStart.get())-1):0):percent));
                    }
                    else{

                        v = coa.get() +(cantRetroactive* coa.get()*
                                (salaryMonthBeforeStart.get().floatValue()!=salaryMonthStart.get().floatValue()?
                                        (asm.get()/(salaryMonthBeforeStart.get()+ comisionMonthBeforeStart.get())-1<percent?
                                                percent-(asm.get()/(salaryMonthBeforeStart.get()+comisionMonthBeforeStart.get())-1):0):percent));
                    }

                    o.getProjections().get(i).setAmount(Math.round(v * 100d) / 100d);
                }
            }
        }

        return componentDTO;
    }


}
