package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResumenComponentDTO {

    private String component;
    private String code;
    private String account;
    private List<MonthProjection> projections;

    public ResumenComponentDTO(String component, String account, List<MonthProjection> projections) {
        this.component = component;
        this.account = account;
        this.projections = projections;
    }
}
