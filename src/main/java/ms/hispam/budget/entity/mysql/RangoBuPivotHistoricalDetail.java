package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "rango_bu_pivot_historical_detail")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RangoBuPivotHistoricalDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String range;
    private Integer idPivot;
    private Double value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_historial", referencedColumnName = "id")
    private RangoBuPivotHistorical rangoBuPivotHistorical;
}
