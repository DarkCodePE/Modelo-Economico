package ms.hispam.budget.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class EmailService {
    private final MailFeignClient mailFgClient;
    private final JavaMailSender emailSender;
    private final SpringTemplateEngine templateEngine;
    private String OCPTOKEN="f60aac663e674ad1a899993ae09c41e9";

    public EmailService(MailFeignClient mailFgClient, JavaMailSender emailSender, SpringTemplateEngine templateEngine) {
        this.mailFgClient = mailFgClient;
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
    }

    public void sendSimpleMessage(String to, String subject, String text) {
        Context context = new Context();
        context.setVariable("message", text);
        context.setVariable("subject", subject);
        context.setVariable("url", "https://personas-hispam.telefonica.com/#/pla/main");
        String body = templateEngine.process("emailTemplate", context);
        MailContent mail = new MailContent();
        mail.setTo(List.of(to));
        mail.setCc(new ArrayList<>());
        mail.setCco(new ArrayList<>());
        mail.setSubject("Reporte de Modelo economico");
        mail.setBody(body);
        mail.setAttachment(new ArrayList<>());
        SendMail sendMail = new SendMail(Arrays.asList(mail));
        mailFgClient.sendEmail(sendMail,OCPTOKEN);
    }
}
