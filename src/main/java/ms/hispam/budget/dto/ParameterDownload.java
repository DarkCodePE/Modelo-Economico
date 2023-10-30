package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParameterDownload {

    private List<ProjectionDTO> data;
    private List<ParametersDTO> parameters;
    private Integer range;
    private String bu;
    private Integer idBu;
    private String period;
    private String nominaFrom;
    private String nominaTo;
    private BaseExternResponse baseExtern;
    private BaseExternResponse bc;
    private ViewDTO viewAnnual;
    private ViewDTO viewMonthly;
}
