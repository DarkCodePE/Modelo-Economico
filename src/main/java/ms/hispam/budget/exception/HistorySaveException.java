package ms.hispam.budget.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
public class HistorySaveException extends RuntimeException {
    public HistorySaveException(String mensaje) {
        super(mensaje);
    }

    public HistorySaveException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
