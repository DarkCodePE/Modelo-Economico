package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.LegalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegalEntityRepository extends JpaRepository<LegalEntity,Integer> {

    List<LegalEntity> findByBu(String bu);
}
