package ms.hispam.budget.repository.sqlserver;

import ms.hispam.budget.dto.PositionBaseline;
import ms.hispam.budget.dto.projections.*;
import ms.hispam.budget.entity.sqlserver.ParametrosGlobal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.transaction.Transactional;
import java.util.Date;
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

        @Query(nativeQuery = true,value = "EXEC get_data_bu @password = :password, @entities = :entities, @periodo = :periodo,@component= :component,@classEmployee=:classEmployee")
    List<HeadcountHistoricalProjection> getHistoricalBuAndPeriodSp(@Param("password") String password,
                                                                   @Param("entities")String entities,
                                                                   @Param("periodo")String periodo,
                                                                   @Param("component")String component,
                                                                   @Param("classEmployee")String classEmployee);

    @Query(nativeQuery = true,value = "EXEC get_cost_po @password = :password, @po = :po")
    Optional<CostPoProjection> getCostPo(@Param("password") String password, @Param("po")String po );

    @Query(nativeQuery = true,value = "select tasa_eur from [cc].T_CC_TasaCambio  where periodo=?1 and divisa=?2")
    Optional<Double> getTypeChange(String period,String divisa);

  //GET REALES

    @Query(nativeQuery = true, value = "EXEC get_reales_bu @bu = :bu, @periodo = :periodo, @accountSap = :accountSap")
    List<RealesProjection> getReales(@Param("bu") String bu, @Param("periodo") String periodo, @Param("accountSap") String accountSap);

    @Query(nativeQuery = true,value = "EXEC get_po_baseline @vperiod = :vperiod,@entity = :entity,@employclass = :employclass,  @vfilter = :vfilter")
    List<PositionBaseline> findPoBaseline(@Param("vperiod") String vperiod,
                                          @Param("entity") String entity,
                                          @Param("employclass") String employclass,
                                          @Param("vfilter") String vfilter);
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO dbo.MODECONOMICO_Proceso (" +
            "FechaEjecucionProyeccion, UsuarioEjecuta, FechaTraspaso, UsuarioTraspaso, " +
            "QRegistros, ImporteTotal, Pais, Proyeccion, ProyeccionNombre, " +
            "PeriodoEjecucion, PositionID, ID_SSFF, ActividadFuncional, " +
            "Ceco, Concepto, CuentaSAP, Mes, Monto) " +
            "VALUES (:fechaEjecucion, :usuarioEjecuta, :fechaTraspaso, :usuarioTraspaso, " +
            ":qRegistros, :importeTotal, :pais, :proyeccion, :proyeccionNombre, " +
            ":periodoEjecucion, :positionId, :idSsff, :actividadFuncional, " +
            ":ceco, :concepto, :cuentaSap, :mes, :monto)", nativeQuery = true)
    void insertModEconomicoProceso(
            @Param("fechaEjecucion") Date fechaEjecucion,
            @Param("usuarioEjecuta") String usuarioEjecuta,
            @Param("fechaTraspaso") Date fechaTraspaso,
            @Param("usuarioTraspaso") String usuarioTraspaso,
            @Param("qRegistros") Long qRegistros,
            @Param("importeTotal") Double importeTotal,
            @Param("pais") String pais,
            @Param("proyeccion") String proyeccion,
            @Param("proyeccionNombre") String proyeccionNombre,
            @Param("periodoEjecucion") Integer periodoEjecucion,
            @Param("positionId") String positionId,
            @Param("idSsff") String idSsff,
            @Param("actividadFuncional") String actividadFuncional,
            @Param("ceco") String ceco,
            @Param("concepto") String concepto,
            @Param("cuentaSap") String cuentaSap,
            @Param("mes") Integer mes,
            @Param("monto") Double monto
    );
}
