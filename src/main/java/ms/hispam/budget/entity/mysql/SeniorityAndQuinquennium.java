package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "seniority_and_quinquennium")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SeniorityAndQuinquennium {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer seniority;

    private BigDecimal quinquennium;
}
