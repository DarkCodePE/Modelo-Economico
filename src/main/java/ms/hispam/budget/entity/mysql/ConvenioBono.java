package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "convenio_bono")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConvenioBono {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "convenio_nivel", nullable = false)
    private String convenioNivel;

    @Column(name = "bono_percentage", nullable = false)
    private Double bonoPercentage;
}
