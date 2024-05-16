package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ConvenioBonoHistorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConvenioBonoHistorialRepository extends JpaRepository<ConvenioBonoHistorial, Integer> {
    List<ConvenioBonoHistorial> findByHistorialProjection_Id(Integer idHistorical);
}
