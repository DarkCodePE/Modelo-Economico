package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "disabled_po_historial")
public class DisabledPoHistorical {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "id_projection_historial")
    private Integer idProjectionHistorial;
    private String po;
    private String idssff;
    @Column(name = "period_from")
    private String periodFrom;
    @Column(name = "period_to")
    private String periodTo;

}
