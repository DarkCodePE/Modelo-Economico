package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResumeSpecDTO {

    private List<ResumenComponentDTO> resumeComponentMonth;
    private List<ResumenComponentDTO> resumeComponentYear;






}
