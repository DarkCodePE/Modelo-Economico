package ms.hispam.budget.repository.mysql;


import ms.hispam.budget.entity.mysql.NominaPaymentComponentLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NominaPaymentComponentLinkRepository extends JpaRepository<NominaPaymentComponentLink, Integer> {
    List<NominaPaymentComponentLink> findAllByNominaConcept_CodeNomina( String codeNomina);
}