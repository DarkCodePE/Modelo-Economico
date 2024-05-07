package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "type_payment_component")
public class TypePaymentComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "payment_component")
    private String paymentComponent;

    @ManyToOne
    @JoinColumn(name = "type_valor", referencedColumnName = "id")
    private TypeValor typeValor;
}
