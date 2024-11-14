package ms.hispam.budget.service;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.countries.ConventArgDTO;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.ConventArg;
import ms.hispam.budget.entity.mysql.ReportJob;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;


import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public interface ProjectionService {

    Page<ProjectionDTO> getProjection(ParametersByProjection projection);

    ProjectionSecondDTO getNewProjection(ParametersByProjection projection, String sessionId, String reportName);

    Config getComponentByBu(String bu);

    Boolean saveProjection( ParameterHistorial projection,String email);

    List<HistorialProjectionDTO> getHistorial( String email);


    ProjectionInformation getHistorialProjection( Integer id);

    Boolean deleteHistorical( Integer id);

    void downloadProjection(ParametersByProjection projection, String userContact, ReportJob job, Integer idBu, String sessionId, String reportName, Long historyId);
    void downloadPlannerAsync(ParametersByProjection projection, Integer type, Integer idBu, String userContact, ReportJob job, String sessionId);
    void downloadCdgAsync(ParametersByProjection projection, Integer type, Integer idBu, String userContact, ReportJob job, String sessionId);
    byte[] downloadProjectionHistorical(Integer id);

    DataBaseMainReponse getDataBase(DataRequest dataRequest);

    List<Bu> findByBuAccess(String email);

    List<ConventArgDTO> getConventArg();

    List<AccountProjection>getAccountsByBu(Integer idBu);

    List<RosetaDTO> getRoseta(Integer bu);

    Boolean saveMoneyOdin(String po,Integer requirement);

    List<PositionBaseline> getPositionBaseline(String period,String filter,String bu,Integer idBu);

    Config getComponentByBuV2(String bu);

    ProjectionSecondDTO getProjectionFromHistoryId(Long historyId);
}
