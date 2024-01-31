package ms.hispam.budget.util;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "MAIL", url = "${mail}")
public interface MailFeignClient {
    @PostMapping(value = "/apimail", consumes = MediaType.APPLICATION_JSON_VALUE)
    boolean sendEmail(@RequestBody SendMail sendMail);
}

