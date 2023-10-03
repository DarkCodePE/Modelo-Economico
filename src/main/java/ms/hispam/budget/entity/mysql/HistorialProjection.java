package ms.hispam.budget.entity.mysql;

import lombok.*;

import java.util.Date;

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
@Table(name = "historial_projection")
public class HistorialProjection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String bu;
    private String name;
    @Column(name = "v_date")
    private Date vDate;
    @Column(name = "v_range")
    private Integer vRange;
    @Column(name = "v_period")
    private String vPeriod;
    @Column(name = "nomina_from")
    private String nominaFrom;
    @Column(name = "nomina_to")
    private String nominaTo;
    @Column(name = "is_top")
    private Boolean isTop;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at")
    private Date createdAt;
    @Column(name = "updated_by")
    private String updatedBy;
    @Column(name = "updated_at")
    private Date updatedAt;
    @Column(name = "id_bu")
    public Integer idBu;

}
