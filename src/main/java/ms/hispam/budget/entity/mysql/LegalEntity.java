package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "legal_entity")
public class LegalEntity {
    @Id
    private Integer id;
    @Column(name = "legal_entity")
    private String legalEntity;
    private String bu;
}
