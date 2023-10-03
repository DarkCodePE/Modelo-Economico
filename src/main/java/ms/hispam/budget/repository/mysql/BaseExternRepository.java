package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.BaseExtern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BaseExternRepository  extends JpaRepository<BaseExtern,Integer> {

    List<BaseExtern> findByBu(Integer bu);
}
