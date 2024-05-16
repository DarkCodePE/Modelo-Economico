package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.RangoBuPivotHistorical;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RangoBuPivotHistoricalRepository extends JpaRepository<RangoBuPivotHistorical, Integer> {
    //IdHistorical
    List<RangoBuPivotHistorical> findByHistorialProjection_Id(Integer idHistorical);
}
