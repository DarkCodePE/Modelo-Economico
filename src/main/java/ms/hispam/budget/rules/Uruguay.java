package ms.hispam.budget.rules;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.util.Shared;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Uruguay {

    public void addParameter(ProjectionDTO po, ParametersByProjection projection){
        //validar que tipo de usuario es
        int typePo=validateTypePo(po.getPoName());
        for (int i = 0; i < po.getComponents().stream().filter(c->c.getType()!=13 || c.getType()!=14).count(); i++) {
            PaymentComponentDTO paymentComponentDTO = po.getComponents().get(i);

            for (int j = 0; j < paymentComponentDTO.getProjections().size(); j++) {
                MonthProjection month = paymentComponentDTO.getProjections().get(j);
                switch (paymentComponentDTO.getType()){
                    case 1:
                        Double variable = getValue(typePo,projection.getParameters(),month.getMonth());
                        month.setAmount(paymentComponentDTO.getAmount()*(1+variable/100));
                        break;
                    case 12:
                        AtomicReference<Double> sn = new AtomicReference<>(0.0);
                                po.getComponents().stream()
                                .filter(c->c.getType()==1).findFirst().ifPresent(f->
                                                f.getProjections().stream().filter(w->w.getMonth().equalsIgnoreCase(month.getMonth()))
                                                        .findFirst().ifPresent(g-> sn.set(g.getAmount())));
                                month.setAmount(sn.get()*paymentComponentDTO.getAmount()/100);
                                break;
                    case 15:
                        AtomicReference<Double> sn1 = new AtomicReference<>(0.0);
                        AtomicReference<Double> fb = new AtomicReference<>(0.0);
                        po.getComponents().stream()
                                .filter(c->c.getType()==1).findFirst().ifPresent(f->
                                        f.getProjections().stream().filter(w->w.getMonth().equalsIgnoreCase(month.getMonth()))
                                                .findFirst().ifPresent(g-> sn1.set(g.getAmount())));
                        projection.getParameters().stream().filter(c->c.getParameter().getId()==9).findFirst().ifPresent(d->fb.set(d.getValue()));
                        Double value = sn1.get()*paymentComponentDTO.getAmount()*fb.get()/100/12;
                        month.setAmount(Shared.getDoubleWithDecimal(value));
                        break;
                }

            }
            //Agregar esto al home
            if(paymentComponentDTO.getType()==15){
                AtomicReference<Double> sn1 = new AtomicReference<>(0.0);
                AtomicReference<Double> fb = new AtomicReference<>(0.0);
                po.getComponents().stream()
                        .filter(c->c.getType()==1).findFirst().ifPresent(f-> sn1.set(f.getAmount()));
                projection.getParameters().stream().filter(c->c.getParameter().getId()==9).findFirst().ifPresent(d->fb.set(d.getValue()));
                Double value = sn1.get()*paymentComponentDTO.getAmount()*fb.get()/100/12;
                paymentComponentDTO.setAmount(Shared.getDoubleWithDecimal(value));
            }


        }
        // AGREGANDO COMPONENTES QUE SON AGREGADOS LUEGO DEL CALCULO
        po.getComponents().add(addPremio(po.getComponents(),projection.getPeriod(),projection.getRange()));
        po.getComponents().add(addSUAT(typePo,projection.getPeriod(),projection.getParameters(),projection.getRange()));
        po.getComponents().add(addAlimentation(typePo,projection.getPeriod(),projection.getParameters(),projection.getRange()));
        po.getComponents().add(addAguinaldo(po.getComponents(),projection.getPeriod(),projection.getRange()));
        po.getComponents().add(addSalarioVacaciones(po.getComponents(),projection.getPeriod(),projection.getRange(),projection.getParameters()));



    }

    public PaymentComponentDTO addSUAT( Integer typeUser,String period,
                                                     List<ParametersDTO> parameters ,Integer range){
        AtomicReference<Double> amount = new AtomicReference<>(0.0);
        parameters.stream().filter(c->c.getParameter().getId()==10).findFirst().ifPresent(c-> amount.set(c.getValue()));
        PaymentComponentDTO decimo = new PaymentComponentDTO();
        decimo.setPaymentComponent("SUAT");
        decimo.setType(21);
        Double value = typeUser==-1 ?amount.get():0.0;
        decimo.setAmount(value);
        decimo.setProjections(Shared.generateMonthProjection(period,range,value));
       return decimo;
    }
    public PaymentComponentDTO addAguinaldo( List<PaymentComponentDTO> components,String period,Integer range){
        AtomicReference<Double> sn = new AtomicReference<>(0.0);
        AtomicReference<Double> guardia = new AtomicReference<>(0.0);
        AtomicReference<Double> premio = new AtomicReference<>(0.0);
        AtomicReference<Double> bono = new AtomicReference<>(0.0);
        AtomicReference<Double> hhee = new AtomicReference<>(0.0);
        components.stream().filter(f->f.getType()==1).findFirst().ifPresent(c->sn.set(c.getAmount()));
        components.stream().filter(f->f.getType()==12).findFirst().ifPresent(c->guardia.set(c.getAmount()));
        components.stream().filter(f->f.getType()==18).findFirst().ifPresent(c->premio.set(c.getAmount()));
        components.stream().filter(f->f.getType()==15).findFirst().ifPresent(c->bono.set(c.getAmount()));
        components.stream().filter(f->f.getType()==16).findFirst().ifPresent(c->hhee.set(c.getAmount()));

        Double value = (sn.get()+guardia.get()+premio.get()+bono.get()+hhee.get())/12;
        PaymentComponentDTO decimo = new PaymentComponentDTO();
        decimo.setPaymentComponent("AGUI");
        decimo.setType(17);
        decimo.setAmount(value);
        decimo.setProjections(Shared.generateMonthProjection(period,range,value));
        for (int j = 0; j < decimo.getProjections().size(); j++) {
            MonthProjection month = decimo.getProjections().get(j);
            AtomicReference<Double> snM = new AtomicReference<>(0.0);
            AtomicReference<Double> guardiaM = new AtomicReference<>(0.0);
            AtomicReference<Double> premioM = new AtomicReference<>(0.0);
            AtomicReference<Double> bonoM = new AtomicReference<>(0.0);
            AtomicReference<Double> hheeM = new AtomicReference<>(0.0);
            components.stream().filter(f->f.getType()==1).findFirst().ifPresent(c->c.getProjections().stream()
                            .filter(h->h.getMonth().equalsIgnoreCase(month.getMonth())).findFirst().ifPresent(k->snM.set(k.getAmount())) );
            components.stream().filter(f->f.getType()==12).findFirst().ifPresent(c->c.getProjections().stream()
                    .filter(h->h.getMonth().equalsIgnoreCase(month.getMonth())).findFirst().ifPresent(k->guardiaM.set(k.getAmount())) );
            components.stream().filter(f->f.getType()==18).findFirst().ifPresent(c->c.getProjections().stream()
                    .filter(h->h.getMonth().equalsIgnoreCase(month.getMonth())).findFirst().ifPresent(k->premioM.set(k.getAmount())) );
            components.stream().filter(f->f.getType()==15).findFirst().ifPresent(c->c.getProjections().stream()
                    .filter(h->h.getMonth().equalsIgnoreCase(month.getMonth())).findFirst().ifPresent(k->bonoM.set(k.getAmount())) );
            components.stream().filter(f->f.getType()==16).findFirst().ifPresent(c->c.getProjections().stream()
                    .filter(h->h.getMonth().equalsIgnoreCase(month.getMonth())).findFirst().ifPresent(k->hheeM.set(k.getAmount())) );
            Double valueM = (snM.get()+guardiaM.get()+premioM.get()+bonoM.get()+hheeM.get())/12;
            month.setAmount(valueM);
        }
        return decimo;
    }

    public PaymentComponentDTO addSalarioVacaciones( List<PaymentComponentDTO> components,String period,Integer range, List<ParametersDTO> parameters ){
        AtomicReference<Double> sn = new AtomicReference<>(0.0);
        AtomicReference<Double> guardia = new AtomicReference<>(0.0);
        AtomicReference<Double> premio = new AtomicReference<>(0.0);
        AtomicReference<Double> ticketAli = new AtomicReference<>(0.0);
        AtomicReference<Double> hhee = new AtomicReference<>(0.0);
        AtomicReference<Double> pdm = new AtomicReference<>(0.0);

        parameters.stream().filter(c->c.getParameter().getId()==16).findFirst().ifPresent(c-> pdm.set(c.getValue()));

        components.stream().filter(f->f.getType()==1).findFirst().ifPresent(c->sn.set(c.getAmount()));
        components.stream().filter(f->f.getType()==12).findFirst().ifPresent(c->guardia.set(c.getAmount()));
        components.stream().filter(f->f.getType()==18).findFirst().ifPresent(c->premio.set(c.getAmount()));
        components.stream().filter(f->f.getType()==16).findFirst().ifPresent(c->hhee.set(c.getAmount()));
        components.stream().filter(f->f.getType()==20).findFirst().ifPresent(c->ticketAli.set(c.getAmount()));


        Double value = (sn.get()+guardia.get()+premio.get()+ticketAli.get()+hhee.get())/30*pdm.get()/12;
        PaymentComponentDTO decimo = new PaymentComponentDTO();
        decimo.setPaymentComponent("SALVAC");
        decimo.setType(19);
        decimo.setAmount(value);
        decimo.setProjections(Shared.generateMonthProjection(period,range,value));
        for (int j = 0; j < decimo.getProjections().size(); j++) {
            MonthProjection month = decimo.getProjections().get(j);
            AtomicReference<Double> snM = new AtomicReference<>(0.0);
            AtomicReference<Double> guardiaM = new AtomicReference<>(0.0);
            AtomicReference<Double> premioM = new AtomicReference<>(0.0);
            AtomicReference<Double> ticketAliM = new AtomicReference<>(0.0);
            AtomicReference<Double> hheeM = new AtomicReference<>(0.0);
            components.stream().filter(f->f.getType()==1).findFirst().ifPresent(c->c.getProjections().stream()
                    .filter(h->h.getMonth().equalsIgnoreCase(month.getMonth())).findFirst().ifPresent(k->snM.set(k.getAmount())) );
            components.stream().filter(f->f.getType()==12).findFirst().ifPresent(c->c.getProjections().stream()
                    .filter(h->h.getMonth().equalsIgnoreCase(month.getMonth())).findFirst().ifPresent(k->guardiaM.set(k.getAmount())) );
            components.stream().filter(f->f.getType()==18).findFirst().ifPresent(c->c.getProjections().stream()
                    .filter(h->h.getMonth().equalsIgnoreCase(month.getMonth())).findFirst().ifPresent(k->premioM.set(k.getAmount())) );

            components.stream().filter(f->f.getType()==16).findFirst().ifPresent(c->c.getProjections().stream()
                    .filter(h->h.getMonth().equalsIgnoreCase(month.getMonth())).findFirst().ifPresent(k->hheeM.set(k.getAmount())) );
            components.stream().filter(f->f.getType()==20).findFirst().ifPresent(c->c.getProjections().stream()
                    .filter(h->h.getMonth().equalsIgnoreCase(month.getMonth())).findFirst().ifPresent(k->ticketAliM.set(k.getAmount())) );
            Double valueM = (snM.get()+guardiaM.get()+premioM.get()+ticketAliM.get()+hheeM.get())/30*pdm.get()/12;
            month.setAmount(valueM);
        }
        return decimo;
    }

    public PaymentComponentDTO addPremio( List<PaymentComponentDTO> components, String period, Integer range){
        AtomicReference<Double> sn = new AtomicReference<>(0.0);
        AtomicReference<Double> pre = new AtomicReference<>(0.0);
        AtomicReference<Double> preCuatri = new AtomicReference<>(0.0);
        components.stream().filter(f->f.getType()==1).findFirst().ifPresent(c->sn.set(c.getAmount()));
        components.stream().filter(f->f.getType()==13).findFirst().ifPresent(c->pre.set(c.getAmount()));
        components.stream().filter(f->f.getType()==14).findFirst().ifPresent(c->preCuatri.set(c.getAmount()));
        Double value = (pre.get()>preCuatri.get()?pre.get():preCuatri.get())*sn.get()/100;
        PaymentComponentDTO decimo = new PaymentComponentDTO();
        decimo.setPaymentComponent("PREMIO");
        decimo.setType(18);
        decimo.setAmount(value);
        decimo.setProjections(Shared.generateMonthProjection(period,range,0.0));
        for (int j = 0; j < decimo.getProjections().size(); j++) {
            MonthProjection month = decimo.getProjections().get(j);
            AtomicReference<Double> snM = new AtomicReference<>(0.0);
            AtomicReference<Double> preM = new AtomicReference<>(0.0);
            AtomicReference<Double> preCuatriM = new AtomicReference<>(0.0);
            components.stream().filter(f->f.getType()==1).findFirst().ifPresent(c->
                    c.getProjections().stream().filter(h->h.getMonth().equalsIgnoreCase(month.getMonth()))
                            .findFirst().ifPresent(g-> snM.set(g.getAmount())));
            components.stream().filter(f->f.getType()==13).findFirst().ifPresent(c-> c.getProjections().stream().filter(h->h.getMonth().equalsIgnoreCase(month.getMonth()))
                    .findFirst().ifPresent(g-> preM.set(g.getAmount())));
            components.stream().filter(f->f.getType()==14).findFirst().ifPresent(c-> c.getProjections().stream().filter(h->h.getMonth().equalsIgnoreCase(month.getMonth()))
                    .findFirst().ifPresent(g-> preCuatriM.set(g.getAmount())));
            Double valueM = (preM.get()>preCuatriM.get()?preM.get():preCuatriM.get())*snM.get()/100;
            month.setAmount(valueM);
        }
        return decimo;
    }
    public PaymentComponentDTO addAlimentation( Integer typeUser,String period,
                                        List<ParametersDTO> parameters ,Integer range){

        PaymentComponentDTO decimo = new PaymentComponentDTO();
        decimo.setType(20);
        decimo.setPaymentComponent("ALI");
        decimo.setProjections(Shared.generateMonthProjection(period,range,0.0));
        for (int j = 0; j < decimo.getProjections().size(); j++) {
            MonthProjection month = decimo.getProjections().get(j);
            AtomicReference<Double> vTicket = new AtomicReference<>(0.0);
            AtomicReference<Double> vDay = new AtomicReference<>(0.0);
            AtomicReference<Double> dayHabil = new AtomicReference<>(0.0);
            AtomicReference<Double> dayVac = new AtomicReference<>(0.0);
            AtomicReference<Double> divSem = new AtomicReference<>(0.0);
            parameters.stream().filter(c->c.getParameter().getId()==11&& Shared.verificarMesEnRango(c.getRange(),month.getMonth()))
                    .findFirst().ifPresent(c-> vTicket.set(c.getValue()));
            parameters.stream().filter(c->c.getParameter().getId()==12 &&Shared.verificarMesEnRango(c.getRange(),month.getMonth()))
                    .findFirst().ifPresent(c-> vDay.set(c.getValue()));
            parameters.stream().filter(c->c.getParameter().getId()==13 &&Shared.verificarMesEnRango(c.getRange(),month.getMonth()))
                    .findFirst().ifPresent(c-> dayHabil.set(c.getValue()));
            parameters.stream().filter(c->c.getParameter().getId()==14 && Shared.verificarMesEnRango(c.getRange(),month.getMonth()))
                    .findFirst().ifPresent(c-> dayVac.set(c.getValue()));
            parameters.stream().filter(c->c.getParameter().getId()==15 && Shared.verificarMesEnRango(c.getRange(),month.getMonth()))
                    .findFirst().ifPresent(c-> divSem.set(c.getValue()));
            Double value = typeUser==2 ?vTicket.get():(dayHabil.get()-dayVac.get())*(vDay.get()/divSem.get());
            if(j==0){
                decimo.setAmount(value);
            }
            month.setAmount(value);
        }
        return decimo;
    }

    public Integer validateTypePo(String poname){
        if(poname.toLowerCase().contains("ceo")){
            return 0;
        }else if(poname.toLowerCase().contains("gerente")){
            return 1;
        }else if(poname.toLowerCase().contains("asesor de atención telefonica")){
            return 2;
        }
        return -1;
    }

    public Double getValue(Integer typePo,List<ParametersDTO> parameters, String month){
        AtomicReference<Double> value = new AtomicReference<>(0.0);
        switch (typePo){
            case 0:
                parameters.stream().filter(f->f.getParameter().getId()==8 &&
                        Shared.verificarMesEnRango(f.getRange(),month)).findFirst().ifPresent(f-> value.set(f.getValue()));
                break;
            case 1:
                parameters.stream().filter(f->f.getParameter().getId()==7  &&
                        Shared.verificarMesEnRango(f.getRange(),month)).findFirst().ifPresent(f-> value.set(f.getValue()));
                break;
            case 2|-1:
                parameters.stream().filter(f->f.getParameter().getId()==6  &&
                        Shared.verificarMesEnRango(f.getRange(),month)).findFirst().ifPresent(f-> value.set(f.getValue()));
                break;
        }
        return value.get();
    }


}
