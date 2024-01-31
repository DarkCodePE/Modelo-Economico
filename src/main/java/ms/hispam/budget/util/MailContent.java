package ms.hispam.budget.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MailContent {

    private String from;
    private List<String> to;
    private List<String> cc;
    private List<String> cco;
    private String subject;
    private String body;
    private List<String> attachment;

}
