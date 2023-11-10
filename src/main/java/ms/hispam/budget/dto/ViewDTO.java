package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewDTO {

    private List<String>headers;
    private List<Object[]> data;
}
