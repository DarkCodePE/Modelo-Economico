package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.RangoBuPivotHistoricalDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RangoBuPivotHistoricalDetailRepository extends JpaRepository<RangoBuPivotHistoricalDetail, Integer> {
    //IdHistorical
    List<RangoBuPivotHistoricalDetail> findByRangoBuPivotHistorical_Id(Integer idHistorical);
}
