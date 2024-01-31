package ms.hispam.budget.entity.mysql;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "report_job")
public class ReportJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String status;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "report_url")
    private String reportUrl;
    @Column(name = "id_ssff")
    private String idSsff;
    @Column(name = "month_base")
    private String monthBase;
    @Column(name = "nomina_range")
    private String nominaRange;
    private String code;
}
