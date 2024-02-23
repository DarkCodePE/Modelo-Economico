package ms.hispam.budget.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RosetaDTO {
    private String code;
    private String name;
    private List<String> payment;
    private List<RosetaDTO> childs;
    private List<MonthProjection> projections;
    private boolean open;

    public RosetaDTO(String code, String name) {
        this.code = code;
        this.name = name;
        this.open=true;
        this.payment=new ArrayList<>();
        this.childs = new ArrayList<>();
        this.projections=new ArrayList<>();
    }
}
