package ms.hispam.budget.dto;

import lombok.*;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.dto.projections.ParameterProjection;
import ms.hispam.budget.entity.mysql.CodeNomina;
import ms.hispam.budget.entity.mysql.ParameterDefault;
import ms.hispam.budget.entity.mysql.RangeBu;

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
    private List<RangeBuDTO> vTemporal;
    private String money;
    private String icon;
    private Boolean vViewPo;
    private String current;
}
