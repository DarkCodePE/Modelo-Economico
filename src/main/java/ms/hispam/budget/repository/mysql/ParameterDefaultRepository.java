package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ParameterDefault;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParameterDefaultRepository extends JpaRepository<ParameterDefault,Integer> {

    List<ParameterDefault> findByBu(Integer id);
}
