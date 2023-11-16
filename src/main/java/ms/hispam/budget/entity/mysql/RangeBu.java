package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "range_bu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RangeBu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "range_of_time")
    private String range;

    @Column(name = "value_of_range")
    private Integer valueOfRange;

    @ManyToOne
    @JoinColumn(name = "pivot_bu_range_id")
    private RangoBuPivot pivotBuRange;
}