package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;
import java.util.Map;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseExternResponse {

    private List<String> headers;
    private List<Map<String, Object>> data;
}
