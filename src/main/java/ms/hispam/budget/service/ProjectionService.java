package ms.hispam.budget.service;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.entity.mysql.Bu;
import org.springframework.web.bind.annotation.RequestParam;


import java.util.List;

public interface ProjectionService {

    public Page<ProjectionDTO> getProjection(ParametersByProjection projection);

    Config getComponentByBu(String bu);

    Boolean saveProjection( ParameterHistorial projection,String email);

    List<HistorialProjectionDTO> getHistorial( String email);


    ProjectionInformation getHistorialProjection( Integer id);

    Boolean deleteHistorical( Integer id);

    byte[] downloadProjection( ParameterDownload projection);

    byte[] downloadProjectionHistorical( Integer id);

    DataBaseMainReponse getDataBase(DataRequest dataRequest);

    List<Bu> findByBuAccess(String email);

    List<AccountProjection>getAccountsByBu(Integer idBu);

    List<RosetaDTO> getRoseta(Integer bu);

    Boolean saveMoneyOdin(String po,Integer requirement);

    byte[] downloadFileType( List<ProjectionDTO> projection , Integer type,Integer idBu);

}
