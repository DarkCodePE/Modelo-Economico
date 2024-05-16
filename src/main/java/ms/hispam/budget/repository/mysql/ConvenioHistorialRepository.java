package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ConvenioHistorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConvenioHistorialRepository extends JpaRepository<ConvenioHistorial, Integer> {
    List<ConvenioHistorial> findByHistorialProjection_Id(Integer id);
}
