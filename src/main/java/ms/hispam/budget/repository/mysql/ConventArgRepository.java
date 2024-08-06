package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ConventArg;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConventArgRepository extends JpaRepository<ConventArg, Integer> {
    // Aquí puedes agregar métodos personalizados de consulta si es necesario
    ConventArg findByConvenio(String convenio);
    ConventArg findByConvenioAndAreaPersonal(String convenio, String areaPersonal);
}
