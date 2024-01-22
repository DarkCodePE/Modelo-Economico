package ms.hispam.budget.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class FormatAmountException extends RuntimeException{
        public FormatAmountException(String mensaje) {
            super(mensaje);
        }
}
