package ms.hispam.budget.dto;

import lombok.*;

@NoArgsConstructor
@Getter
@Setter
@Builder
@AllArgsConstructor
public class OfficialProHistoryDTO {
    private String bu;
    private Long historyId;
    private String userContact;
}
