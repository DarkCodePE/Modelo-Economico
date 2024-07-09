package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationRuleRepository extends JpaRepository<ValidationRule, Long> {
    List<ValidationRule> findByBu_Bu(Integer bu);
}
