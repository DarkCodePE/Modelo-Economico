package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Frecuently {
    @Id
    private String code;
    private Double factor;
}
