package ms.hispam.budget.service;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.ReportJob;
import org.springframework.web.bind.annotation.RequestParam;


import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ProjectionService {

    public Page<ProjectionDTO> getProjection(ParametersByProjection projection);

    Config getComponentByBu(String bu);

    Boolean saveProjection( ParameterHistorial projection,String email);

    List<HistorialProjectionDTO> getHistorial( String email);


    ProjectionInformation getHistorialProjection( Integer id);

    Boolean deleteHistorical( Integer id);

    void downloadProjection(ParameterDownload projection, String userContact, ReportJob job);

    byte[] downloadProjectionHistorical(Integer id);

    DataBaseMainReponse getDataBase(DataRequest dataRequest);

    List<Bu> findByBuAccess(String email);

    List<AccountProjection>getAccountsByBu(Integer idBu);

    List<RosetaDTO> getRoseta(Integer bu);

    Boolean saveMoneyOdin(String po,Integer requirement);

    byte[] downloadFileType( List<ProjectionDTO> projection , Integer type,Integer idBu);

}
