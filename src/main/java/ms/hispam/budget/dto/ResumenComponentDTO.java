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
    private String account;
    private List<MonthProjection> projections;


}
