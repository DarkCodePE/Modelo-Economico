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
public class Parameters {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String description;
    @Column(name = "type_valor")
    private Integer typeValor;
    @Column(name = "type_payment_component")
    private Integer typePaymentComponent;
    @Column(name = "is_retroactive")
    private Boolean isRetroactive;
    @Column(name = "is_range")
    private Boolean isRange;
    @Column(name = "all_period")
    private Boolean allPeriod;
    @Column(name = "month_restringed")
    private String monthRestringed;
}
