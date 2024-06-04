package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.EmployeeClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeClassificationRepository extends JpaRepository<EmployeeClassification, Long> {
    //Optional<EmployeeClassification> findByCategory(String category);
}