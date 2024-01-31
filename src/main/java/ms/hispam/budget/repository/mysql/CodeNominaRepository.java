package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.CodeNomina;
import ms.hispam.budget.entity.mysql.NominaPaymentComponentLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeNominaRepository extends JpaRepository<CodeNomina,Integer> {

    List<CodeNomina> findByIdBu(Integer bu);
    List<CodeNomina> findAllByNameContaining(String name);
}
