package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.RangeBu;
import ms.hispam.budget.entity.mysql.RangoBuPivot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RangeBuRepository extends JpaRepository<RangeBu, Integer> {
    RangeBu findByPivotBuRange(RangoBuPivot pivotBuRange);
    List<RangeBu> findByPivotBuRange_Id(Long pivotBuRangeId);
}
