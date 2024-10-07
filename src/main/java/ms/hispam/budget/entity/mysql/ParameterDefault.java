package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "parameter_default")
public class ParameterDefault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer bu;
    @Column(name = "v_parameter")
    private Integer vParameter;
    private Double value;
    private String periodo;
}
