package ms.hispam.budget.service;

import ms.hispam.budget.dto.*;


import java.util.List;

public interface ProjectionService {

    public Response<Page<ProjectionDTO>> getProjection(ParametersByProjection projection);

    Config getComponentByBu(String bu);

    Response<Boolean> saveProjection( ParameterHistorial projection,String email);

    List<HistorialProjectionDTO> getHistorial( String email);


    Response<Page<ProjectionDTO>> getHistorialProjection( Integer id,Integer page,Integer size);

    Response<Boolean> deleteHistorical( Integer id);

    byte[] downloadProjection( ParametersByProjection projection);

    byte[] downloadProjectionHistorical( Integer id);

}
