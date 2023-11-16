package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.RangoBuPivot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RangeBuPivotRepository extends JpaRepository<RangoBuPivot, Long> {
    List<RangoBuPivot> findByBu_Id(Integer buId);
    List<RangoBuPivot> findByBu(Bu bu);
}
