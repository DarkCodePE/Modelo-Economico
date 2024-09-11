package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.entity.mysql.ReportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReportJobRepository extends JpaRepository<ReportJob, Integer>{
    List<ReportJob> findByIdSsff(String mail);
    List<ReportJob> findByIdSsffOrderByCreationDateDesc(String idSsff);
    //native query report by user and BU
    @Query("SELECT r FROM ReportJob r LEFT JOIN FETCH r.parameters WHERE r.idSsff = ?1 AND r.idBu = ?2 ORDER BY r.creationDate DESC")
    List<ReportJob> findByIdSsffAndBu(String idSsff, Integer bu);
}
