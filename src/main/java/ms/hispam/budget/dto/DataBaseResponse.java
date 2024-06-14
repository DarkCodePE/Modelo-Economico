package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataBaseResponse {

    private String po;
    private String idssff;
    private String poName;
    private String typeEmployee;
    //fecha de nacimiento
    private String birthDate;
    //fecha de contratacion
    private String hiringDate;
    //convent
    private String convent;
    //nivel
    private String level;
    private List<ComponentAmount> components;
    private String categoryLocal;
    private String estadoVacante;
}
