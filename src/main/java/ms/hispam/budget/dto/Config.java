package ms.hispam.budget.dto;

import lombok.*;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.dto.projections.ParameterProjection;
import ms.hispam.budget.entity.mysql.BaseExtern;
import ms.hispam.budget.entity.mysql.CodeNomina;
import ms.hispam.budget.entity.mysql.ParameterDefault;

import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Config {
    private List<ComponentProjection> components;
    private List<ParameterProjection> parameters;
    private List<OperationResponse> baseExtern;
    private List<CodeNomina> nominas;
    private List<ParameterDefault> vDefault;
    private String money;
    private String icon;
}
