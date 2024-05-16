package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Timestamp;
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

    @ManyToOne
    @JoinColumn(name = "bu_id", referencedColumnName = "id")
    private Bu bu;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @ManyToOne
    @JoinColumn(name = "id_historial", referencedColumnName = "id")
    private HistorialProjection historialProjection;

    @Column(name = "user_email")
    private String userEmail;

    @OneToMany(mappedBy = "rangoBuPivotHistorical")
    private Set<RangoBuPivotHistoricalDetail> rangoBuPivotHistoricalDetails;

    @OneToMany(mappedBy = "rangoBuPivotHistorical")
    private Set<RangeBuHistorical> rangeBuHistoricals;

}