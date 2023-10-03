package ms.hispam.budget.service;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.entity.mysql.Bu;
import org.springframework.web.bind.annotation.RequestParam;


import java.util.List;

public interface ProjectionService {

    public Response<Page<ProjectionDTO>> getProjection(ParametersByProjection projection);

    Config getComponentByBu(String bu);

    Response<Boolean> saveProjection( ParameterHistorial projection,String email);

    List<HistorialProjectionDTO> getHistorial( String email);


    ProjectionInformation getHistorialProjection( Integer id);

    Response<Boolean> deleteHistorical( Integer id);

    byte[] downloadProjection( ParametersByProjection projection);

    byte[] downloadProjectionHistorical( Integer id);

    DataBaseMainReponse getDataBase(DataRequest dataRequest);

    List<Bu> findByBuAccess(String email);

    Response<List<AccountProjection>>getAccountsByBu(Integer idBu);

    List<RosetaDTO> getRoseta(Integer bu);

    Boolean saveMoneyOdin(String po,Integer requirement);

}
