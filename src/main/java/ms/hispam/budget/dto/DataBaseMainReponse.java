package ms.hispam.budget.dto;

import lombok.*;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.entity.mysql.CodeNomina;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataBaseMainReponse {

    private List<DataBaseResponse> data;
    private List<ComponentProjection> components;
    private List<CodeNomina> nominas;
    private List<DataBaseResponse> comparing;
}
