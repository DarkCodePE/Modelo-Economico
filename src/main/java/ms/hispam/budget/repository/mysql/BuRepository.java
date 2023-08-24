package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.Bu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BuRepository extends JpaRepository<Bu,Integer> {

    Optional<Bu> findByBu(String bu);
}
