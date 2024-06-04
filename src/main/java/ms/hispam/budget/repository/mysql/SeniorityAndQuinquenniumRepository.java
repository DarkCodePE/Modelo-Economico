package ms.hispam.budget.repository.mysql;
import ms.hispam.budget.entity.mysql.SeniorityAndQuinquennium;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeniorityAndQuinquenniumRepository extends JpaRepository<SeniorityAndQuinquennium, Integer> {
}