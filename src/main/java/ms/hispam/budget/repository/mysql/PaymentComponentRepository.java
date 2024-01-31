package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.PaymentComponent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentComponentRepository extends JpaRepository<PaymentComponent, Integer> {
}
