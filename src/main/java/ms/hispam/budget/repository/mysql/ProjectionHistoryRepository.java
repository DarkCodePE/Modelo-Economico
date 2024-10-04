package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ProjectionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectionHistoryRepository extends JpaRepository<ProjectionHistory, Long> {
    List<ProjectionHistory> findByUserId(Long userId);
    Optional<ProjectionHistory> findByIdAndUserId(Long id, Long userId);
}
