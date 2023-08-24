package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Bu {
    @Id
    private Integer id;
    private String bu;
    private String money;
    private String icon;
}
