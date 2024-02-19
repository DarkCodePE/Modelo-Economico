package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.Convenio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


public interface ConvenioRepository extends JpaRepository<Convenio, Integer> {
}
