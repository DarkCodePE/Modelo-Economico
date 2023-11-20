package ms.hispam.budget.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentComponentDTO implements Cloneable{
    private String paymentComponent;
    private Integer type;
    private BigDecimal amount;
    private List<MonthProjection> projections;
    private Boolean show;
    private String name;

    public BigDecimal getAmount() {
        if(amount==null){
            return BigDecimal.ZERO;
        }
        return amount;
    }
    public PaymentComponentDTO(String paymentComponent, BigDecimal amount, List<MonthProjection> projections) {
        this.paymentComponent = paymentComponent;
        this.amount = amount;
        this.projections = projections;
    }

    public PaymentComponentDTO createWithProjections() {
        if ("SALARY".equalsIgnoreCase(paymentComponent)) {
            PaymentComponentDTO newComponent = new PaymentComponentDTO("PROV_AGUINALDO", calculateAguinaldoAmount(), cloneProjections());
            return newComponent;
        } else {
            // Lógica para otros componentes si es necesario
            return this; // O devuelve una nueva instancia según tus requisitos
        }
    }
    private BigDecimal calculateAguinaldoAmount() {
        // Lógica para calcular el monto del aguinaldo basado en las proyecciones
        // Puedes adaptar esto según tus requisitos específicos
        // Aquí, simplemente sumamos los montos de las proyecciones
        return projections.stream()
                .map(MonthProjection::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<MonthProjection> cloneProjections() {
        if (projections == null) {
            return null;
        }

        List<MonthProjection> clonedProjections = new ArrayList<>();
        for (MonthProjection projection : projections) {
            clonedProjections.add(projection.clone());
        }
        return clonedProjections;
    }
    @Override
    public PaymentComponentDTO clone() {
        try {
            PaymentComponentDTO cloned = (PaymentComponentDTO) super.clone();

            // Copia profunda de la lista de projections
            if (projections != null) {
                List<MonthProjection> clonedProjections = new ArrayList<>();
                for (MonthProjection projection : projections) {
                    clonedProjections.add(projection.clone());
                }
                cloned.setProjections(clonedProjections);
            }

            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Error al clonar PaymentComponentDTO", e);
        }
    }
}
