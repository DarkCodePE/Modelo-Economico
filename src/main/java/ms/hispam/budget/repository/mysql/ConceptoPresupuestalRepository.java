package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ConceptoPresupuestal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConceptoPresupuestalRepository extends JpaRepository<ConceptoPresupuestal, Integer> {
}
