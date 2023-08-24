package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.dto.projections.ParameterProjectionBD;
import ms.hispam.budget.entity.mysql.Parameters;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DemoRepository extends JpaRepository<Parameters,Integer> {
    @Query(nativeQuery = true,value = "call sp_component_by_bu(?1)")
    List<ComponentProjection> getComponentByBu(String bu);

    @Query(nativeQuery = true,value = "call sp_parameter_historical(?1)")
    List<ParameterProjectionBD> getParameter_historical(Integer id);

}
