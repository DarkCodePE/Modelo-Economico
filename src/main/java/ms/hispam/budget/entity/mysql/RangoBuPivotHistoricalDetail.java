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

    @Column(name = "`range`")
    private String range;

    @Column(name = "value")
    private Double value;

    @ManyToOne
    @JoinColumn(name = "id_pivot", referencedColumnName = "id", insertable = true, updatable = true)
    private RangoBuPivotHistorical rangoBuPivotHistorical;

    // Getters and Setters
}
