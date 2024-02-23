package ms.hispam.budget.repository.mysql;


import ms.hispam.budget.entity.mysql.NominaPaymentComponentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface NominaPaymentComponentLinkRepository extends JpaRepository<NominaPaymentComponentLink, Integer> {

}