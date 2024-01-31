package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ReportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportJobRepository extends JpaRepository<ReportJob, Integer>{
    List<ReportJob> findByIdSsff(String mail);
}
