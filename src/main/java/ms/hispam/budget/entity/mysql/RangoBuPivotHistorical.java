package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

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

    @OneToMany(mappedBy = "rangoBuPivotHistorical", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RangoBuPivotHistoricalDetail> details = new HashSet<>();
}
