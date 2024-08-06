package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nomina_concept")
public class CodeNomina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "id_bu")
    private Integer idBu;
    @Column(name = "code_nomina")
    private String codeNomina;
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "range_type")
    private NominaRangeType rangeType;
    public NominaRangeType getRangeType() {
        return rangeType != null ? rangeType : NominaRangeType.ALL;
    }
}
