package ms.hispam.budget.dto;

import lombok.*;
import ms.hispam.budget.entity.mysql.Convenio;
import ms.hispam.budget.entity.mysql.ConvenioBono;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParameterHistorial {
    private String bu;
    private String name;
    private String period;
    private Integer range;
    private String nominaFrom;
    private String nominaTo;
    private List<DisabledPoDTO> disabledPo;
    private List<ParametersDTO> parameters;
    private Boolean isTop;
    private Integer idBu;
    private BaseExternResponse baseExtern;
    private BaseExternResponse bc;
    private List<RangeBuDTO> temporalParameters;
    private List<Convenio> convenio;
    private List<ConvenioBono> convenioBono;
}
