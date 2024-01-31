package ms.hispam.budget.entity.mysql;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
@Entity
@Table(name = "nomina_payment_component_link")
@Getter
@Setter
@NoArgsConstructor
public class NominaPaymentComponentLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "nomina_concept_id", referencedColumnName = "id")
    private CodeNomina nominaConcept;

    @ManyToOne
    @JoinColumn(name = "payment_component_id", referencedColumnName = "id", nullable = false)
    private PaymentComponent paymentComponent;
}
