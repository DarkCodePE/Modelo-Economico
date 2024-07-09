package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "convent_arg")
public class ConventArg {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "area_personal", nullable = false)
    private String areaPersonal;

    @Column(name = "convenio", nullable = false)
    private String convenio;

    @Column(name = "tipo_convenio", nullable = false)
    private String tipoConvenio;
}