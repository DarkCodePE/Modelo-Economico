package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.dto.projections.ParameterProjection;
import ms.hispam.budget.entity.mysql.Parameters;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ParameterRepository extends JpaRepository<Parameters,Integer> {

    @Query(nativeQuery = true,value = "call sp_parameter(?1)")
    List<ParameterProjection> getParameterBu(String bu);
}
