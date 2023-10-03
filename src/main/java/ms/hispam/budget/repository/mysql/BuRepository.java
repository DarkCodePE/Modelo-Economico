package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.Bu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BuRepository extends JpaRepository<Bu,Integer> {

    Optional<Bu> findByBu(String bu);
    @Query(nativeQuery = true,value = "select b.* from budget.bu b left join budget.bu_access ba on ba.bu =b.id where ba.email =?1")
    List<Bu> findByBuAccess(String email);
}
