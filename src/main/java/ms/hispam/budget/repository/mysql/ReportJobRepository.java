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
    @Query(value = "SELECT * FROM report_job WHERE id_ssff = ?1 AND id_bu = ?2 ORDER BY creation_date DESC", nativeQuery = true)
    List<ReportJob> findByIdSsffAndBu(String idSsff, Integer bu);
}
