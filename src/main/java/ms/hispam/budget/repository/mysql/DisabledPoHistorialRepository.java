package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.DisabledPoHistorical;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisabledPoHistorialRepository extends JpaRepository<DisabledPoHistorical,Integer> {

    List<DisabledPoHistorical> findByIdProjectionHistorial(Integer idProjection);
}
