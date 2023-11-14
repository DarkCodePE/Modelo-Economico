package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.DaysVacationOfTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DaysVacationOfTimeRepository extends JpaRepository<DaysVacationOfTime, Integer> {
}
