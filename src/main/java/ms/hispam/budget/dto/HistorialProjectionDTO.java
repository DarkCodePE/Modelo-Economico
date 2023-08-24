package ms.hispam.budget.dto;

import lombok.*;

import java.util.Date;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialProjectionDTO {

    private Integer id;
    private String bu;
    private String name;
    private Date vDate;
    private Integer vRange;
    private String vPeriod;
    private Date createdAt;
}
