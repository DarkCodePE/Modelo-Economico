package ms.hispam.budget.entity.mysql;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "parameter_bu")
public class ParameterBu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_bu")
    private Integer idBu;

    @Column(name = "id_parameter")
    private Integer idParameter;

    @ManyToOne
    @JoinColumn(name = "id_report_job")
    private ReportJob reportJob;
}
