package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.JsonTemp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JsonDatosRepository  extends JpaRepository<JsonTemp,Integer> {
}
