package ms.hispam.budget.entity.mysql;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "payment_component")
@Getter
@Setter
@NoArgsConstructor
public class PaymentComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "bu")
    private Integer bu;

    @Column(name = "payment_component")
    private String paymentComponent;

    @Column(name = "type_payment_component")
    private Integer typePaymentComponent;

    @Column(name = "is_component")
    private Boolean isComponent;

    @Column(name = "`show`")
    private Boolean show;
}
