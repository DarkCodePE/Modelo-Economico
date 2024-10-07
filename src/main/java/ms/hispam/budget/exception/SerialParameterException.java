package ms.hispam.budget.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
public class SerialParameterException  extends RuntimeException{
    public SerialParameterException(String mensaje) {
        super(mensaje);
    }

    public SerialParameterException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
