package ms.hispam.budget.entity.mysql;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(name = "conceptos_presupuestales")
public class ConceptoPresupuestal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ConceptoPresupuestal")
    private String conceptoPresupuestal;

    @Column(name = "Gratificacion")
    private BigDecimal gratificacion;

    @Column(name = "CTS")
    private BigDecimal cts;

    @Column(name = "Essalud")
    private BigDecimal essalud;

    @Column(name = "BonifExtTemp")
    private BigDecimal bonifExtTemp;
}