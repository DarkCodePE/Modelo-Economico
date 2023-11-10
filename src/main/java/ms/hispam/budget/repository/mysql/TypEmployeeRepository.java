package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.TypeEmployeeProjection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TypEmployeeRepository extends JpaRepository<TypeEmployeeProjection,Integer> {
    List<TypeEmployeeProjection> findByBu(Integer bu);

}
