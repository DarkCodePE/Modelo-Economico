package ms.hispam.budget.rules;

import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.util.Shared;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Colombia {

    public List<PaymentComponentDTO> revisionSalary(List<PaymentComponentDTO> componentDTO, ParametersDTO dto  ){
        double differPercent=0.0;
        if(Boolean.TRUE.equals(dto.getIsRetroactive())){
            int idxStart;
            int idxEnd;
            String[]   period;
            period = dto.getPeriodRetroactive().split("-");
            idxStart=  Shared.getIndex(componentDTO.get(1).getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()),period[0]);
            idxEnd=  Shared.getIndex(componentDTO.get(1).getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()),period.length==1? period[0]:period[1]);
            AtomicReference<Double> salaryFirst= new AtomicReference<>(0.0);
            AtomicReference<Double> salaryEnd= new AtomicReference<>(0.0);
            AtomicReference<Double> comisionFirst= new AtomicReference<>(0.0);
            AtomicReference<Double> comisionEnd= new AtomicReference<>(0.0);
            componentDTO.stream().filter(c->c.getType()==1).findFirst().ifPresent(l->{
                salaryFirst.set(l.getProjections().get(idxStart).getAmount());
                salaryEnd.set(l.getProjections().get(idxEnd).getAmount());
            });
            componentDTO.stream().filter(c->c.getType()==2).findFirst().ifPresent(l->{
                comisionFirst.set(l.getProjections().get(idxStart).getAmount());
                comisionEnd.set(l.getProjections().get(idxEnd).getAmount());
            });
            differPercent=(salaryEnd.get()+comisionEnd.get())/(salaryFirst.get()+comisionFirst.get())-1;
        }
        double percent = dto.getValue()/100;
        for(PaymentComponentDTO o : componentDTO.stream().filter(f->(
                f.getType()==1|| f.getType()==2 || f.getType()==7)).collect(Collectors.toList())){

            int idx = Shared.getIndex(o.getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()),dto.getPeriod());
            for (int i = idx; i < o.getProjections().size(); i++) {
                double v=0;
                double amount = i==0?o.getProjections().get(i).getAmount(): o.getProjections().get(i-1).getAmount();
                o.getProjections().get(i).setAmount(amount);
                if(o.getProjections().get(i).getMonth().equalsIgnoreCase(dto.getPeriod())){
                    if(o.getType()==1 ||o.getType()==7|| o.getType()==2 ){
                        if(o.getType()==7){
                            amount=o.getAmount();
                        }
                        v = amount* (1+(differPercent>=percent?0:percent-differPercent));
                    }
                    o.getProjections().get(i).setAmount(Math.round(v * 100d) / 100d);
                }
            }
        }



        return componentDTO;
    }


    public List<PaymentComponentDTO> salMin(List<PaymentComponentDTO> componentDTO, List<ParametersDTO> parameters   ){
        //SI(Y($D4<>"PRA";$D4<>"T";$D4<>"JP";$D4<>"APR");
      /*  if (parameters.stream().anyMatch(p->p.getPaymentComponent().equalsIgnoreCase("PRA")||
                p.getPaymentComponent().equalsIgnoreCase("T")||
                p.getPaymentComponent().equalsIgnoreCase("JP")||
                p.getPaymentComponent().equalsIgnoreCase("APR"))){
            return componentDTO;
        }*/
        //SI(MES(S$12)<>3;SI($F4<>""; SI(R13<=S$3;S$3;R13)
        if (componentDTO.stream().anyMatch(p->p.getProjections().stream().anyMatch(m->m.getMonth().equalsIgnoreCase("MAR")))){
            if(componentDTO.stream().anyMatch(p->p.getProjections().stream().anyMatch(m->m.getMonth().equalsIgnoreCase("MAR")))){
                return componentDTO;
            }
        }
        //SI(R13<S$4;S$4;R13))
        return null;
    }

}
