package ms.hispam.budget.exception.payload;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class ApiResponse {
    private Date date = new Date();
    private String message;
    private String url;

    public ApiResponse(String mensaje, String url) {
        this.message = mensaje;
        this.url = url.replace("uri=","");
    }
}
