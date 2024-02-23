package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResumeProjecDTO {
    private  List<ResumenComponentDTO> resumeComponent;
}
