package ms.hispam.budget.exception.payload;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class ApiResponse<T> {
    private Date date = new Date();
    private T message;
    private String url;

    public ApiResponse(T mensaje, String url) {
        this.message = mensaje;
        this.url = url.replace("uri=","");
    }
}
