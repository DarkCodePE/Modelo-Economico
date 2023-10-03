package ms.hispam.budget.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRequest {
    private String po;
    private Integer idBu;
    private String bu;
    private String period;
    private String nominaFrom;
    private String nominaTo;
    private String periodComparing;
    private String nominaFromComparing;
    private String nominaToComparing;
    private boolean isComparing;
}
