package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.EdadSV;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EdadSVRepository extends JpaRepository<EdadSV, Long> {
}
