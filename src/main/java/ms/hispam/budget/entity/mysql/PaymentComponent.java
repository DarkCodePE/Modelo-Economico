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

    @ManyToOne
    @JoinColumn(name = "type_payment_component", referencedColumnName = "id")
    private TypePaymentComponent typePaymentComponentEntity;

    @Column(name = "is_component")
    private Boolean isComponent;

    @Column(name = "`show`")
    private Boolean show;
    //is_additional
    @Column(name = "is_additional")
    private Boolean isAdditional;
}
