package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.PoHistorialExtern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PoHistorialExternRepository extends JpaRepository<PoHistorialExtern,Integer> {

    List<PoHistorialExtern> findByIdHistorial(Integer id);

}
