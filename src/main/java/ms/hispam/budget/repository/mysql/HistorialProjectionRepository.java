package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.HistorialProjection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialProjectionRepository extends JpaRepository<HistorialProjection,Integer> {

    List<HistorialProjection> findByCreatedByOrderByCreatedAtDesc(String email);


}
