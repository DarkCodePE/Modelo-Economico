package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "business_case_historial")
public class BusinessCaseHistorial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String po;
    private String component;
    private String nvalue;
    @Column(name = "id_historial")
    private Integer idHistorial;
}
