package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.BusinessCaseHistorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessCaseRepository extends JpaRepository<BusinessCaseHistorial,Integer> {

    List<BusinessCaseHistorial> findByIdHistorial(Integer id);
}
