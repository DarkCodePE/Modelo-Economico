package ms.hispam.budget.repository.mysql;

import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.dto.projections.ParameterProjectionBD;
import ms.hispam.budget.dto.projections.PaymentRoseta;
import ms.hispam.budget.entity.mysql.Parameters;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DemoRepository extends JpaRepository<Parameters,Integer> {
    @Query(nativeQuery = true,value = "call sp_component_by_bu(?1)")
    List<ComponentProjection> getComponentByBu(String bu);

    @Query(nativeQuery = true,value = "call sp_parameter_historical(?1)")
    List<ParameterProjectionBD> getParameter_historical(Integer id);

    @Query(nativeQuery = true,value = "call sp_component_account(?1)")
    List<AccountProjection>getAccount(Integer bu);

    @Query(nativeQuery = true,value = "call sp_rosseta(?1)")
    List<PaymentRoseta> getPaymentRosseta(Integer id);
    @Modifying(clearAutomatically = true,flushAutomatically = true)
    @Transactional
    @Query(nativeQuery = true,value = "call sp_copy_cost(?1,?2,?3)")
    void saveCostPo(Integer id,String budget,String currency);

    @Modifying(clearAutomatically = true,flushAutomatically = true)
    @Transactional
    @Query(nativeQuery = true,value = "call insert_json_extern(?1,?2)")
    void insertHistorialExtern(Integer id,Integer type);






}
