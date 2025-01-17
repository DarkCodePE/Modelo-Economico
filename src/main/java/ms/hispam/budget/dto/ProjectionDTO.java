package ms.hispam.budget.dto;

import lombok.*;


import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionDTO {

    private String idssff;
    private String po;
    private String poName;
    private List<PaymentComponentDTO> components;
    private String classEmployee;
    private LocalDate fNac;
    private LocalDate fContra;
    private String areaFuncional;
    private String division;
    private String cCostos;
    private String convent;
    private String level;
    //TODO: PARA PERU INCLUIR PUESTOS ESPECIFICOS DE LA CRUZE CON LA TABLA PARAMETRICA
    private String categoryLocal;
    private String estadoVacante;
}
