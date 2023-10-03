package ms.hispam.budget.repository.sqlserver;

import ms.hispam.budget.dto.projections.ComponentCashProjection;
import ms.hispam.budget.dto.projections.ComponentNominaProjection;
import ms.hispam.budget.dto.projections.CostPoProjection;
import ms.hispam.budget.dto.projections.HeadcountHistoricalProjection;
import ms.hispam.budget.entity.sqlserver.ParametrosGlobal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParametersRepository extends JpaRepository<ParametrosGlobal,Integer> {

    @Query(nativeQuery = true,value = "SELECT Position_ID position,Nombre_del_puesto poname, ID_de_SSFF idssff, Cod_Entidad_Legal entitylegal,Genero gender,ci.Unidad_de_negocio bu,Primary_workstream wk,Division_codigo division,Departamento_codigo department FROM T_SSFF_Headcount_Cierre_Historia ci  WHERE ci.Cod_Entidad_Legal in (?2) and Clase_de_empleado='EMP' AND ci.Periodo =?1 ORDER BY Position_ID ASC")
    List<HeadcountHistoricalProjection> getHistoricalBuAndPeriod(String period,List<String > entities);

    @Query(nativeQuery = true,value = "EXEC get_fte_ppto @password = :password, @entities = :entities, @periodo = :periodo")
    List<ComponentCashProjection> getComponentCash(@Param("password") String password, @Param("entities")String entities,
                                                   @Param("periodo") String periodo);

    @Query(nativeQuery = true,value = "EXEC get_nom_ppto @password = :password, @bu = :bu, @p_ini = :p_ini, @p_fin = :p_fin,@co_nomina= :co_nomina")
    List<ComponentNominaProjection> getcomponentNomina(@Param("password") String password, @Param("bu")String bu,
                                                       @Param("p_ini") String p_ini, @Param("p_fin") String p_fin,
                                                       @Param("co_nomina") String co_nomina);

    @Query(nativeQuery = true,value = "EXEC get_data_bu @password = :password, @entities = :entities, @periodo = :periodo,@component= :component")
    List<HeadcountHistoricalProjection> getHistoricalBuAndPeriodSp(@Param("password") String password,
                                                                   @Param("entities")String entities,
                                                                   @Param("periodo")String periodo,
                                                                   @Param("component")String component);

    @Query(nativeQuery = true,value = "EXEC get_cost_po @password = :password, @po = :po")
    Optional<CostPoProjection> getCostPo(@Param("password") String password, @Param("po")String po );




}
