package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "range_bu_historical")
public class RangeBuHistorical {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "range_of_time", nullable = false)
    private String rangeOfTime;

    @Column(name = "value_of_range", nullable = false)
    private Double valueOfRange;

    @ManyToOne
    @JoinColumn(name = "pivot_bu_range_id", referencedColumnName = "id")
    private RangoBuPivotHistorical rangoBuPivotHistorical;

}
