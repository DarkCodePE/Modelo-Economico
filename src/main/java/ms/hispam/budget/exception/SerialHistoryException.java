package ms.hispam.budget.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
public class SerialHistoryException extends RuntimeException{
    public SerialHistoryException(String mensaje) {
        super(mensaje);
    }

    public SerialHistoryException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}