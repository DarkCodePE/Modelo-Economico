package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> {
    private Boolean success;
    private Integer status;
    private String error;
    private String message;
    private   T data;


}
