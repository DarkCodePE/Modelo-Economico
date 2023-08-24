package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ParameterProjection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParameterProjectionRepository extends JpaRepository<ParameterProjection,Integer> {

    long deleteByIdHistorial(Integer id);
}
