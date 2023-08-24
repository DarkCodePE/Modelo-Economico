package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "parameter_projection")

public class ParameterProjection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "id_historial")
    private Integer idHistorial;
    @Column(name = "id_parameter")
    private Integer idParameter;
    private Double value;
    @Column(name = "v_period")
    private String vPeriod;
    @Column(name = "v_range")
    private String vRange;
    @Column(name = "v_isretroactive")
    private Boolean vIsretroactive;
    @Column(name = "v_period_retroactive")
    private String vPeriodRetroactive;
}
