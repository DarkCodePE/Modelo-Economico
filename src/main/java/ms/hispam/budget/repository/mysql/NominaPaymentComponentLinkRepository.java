package ms.hispam.budget.repository.mysql;


import ms.hispam.budget.entity.mysql.NominaPaymentComponentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface NominaPaymentComponentLinkRepository extends JpaRepository<NominaPaymentComponentLink, Integer> {
    @Query(value = "SELECT * FROM nomina_payment_component_link", nativeQuery = true)
    List<NominaPaymentComponentLink> findAllWithNativeQuery();
    @Query("SELECT n FROM NominaPaymentComponentLink n WHERE n.nominaConcept.idBu = :bu")
    List<NominaPaymentComponentLink> findByBu(Integer bu);
}