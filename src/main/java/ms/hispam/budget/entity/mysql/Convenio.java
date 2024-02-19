package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "convenio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Convenio {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(name = "convenio_name", nullable = false)
    private String convenioName;

    @Column(name = "retiro_percentage", nullable = false)
    private double retiroPercentage;

    @Column(name = "imss_percentage", nullable = false)
    private double imssPercentage;

    @Column(name = "infonavit_percentage", nullable = false)
    private double infonavitPercentage;
}
