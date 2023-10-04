package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

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
    private String flag;
    @Column(name = "v_view_po")
    private Boolean vViewPo;
}
