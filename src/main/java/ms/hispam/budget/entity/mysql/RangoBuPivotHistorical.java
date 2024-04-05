package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "rango_bu_pivot_historical")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RangoBuPivotHistorical {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_historial")
    private Integer idHistorial;

    @Column(name = "id_rango_bu_pivot")
    private Integer idRangoBuPivot;

    // Supongamos que hay otros valores que deseas capturar en el histórico,
    // por ejemplo, el valor del rango en un periodo específico
    private Double value;

    @Column(name = "v_period")
    private String vPeriod;

}
