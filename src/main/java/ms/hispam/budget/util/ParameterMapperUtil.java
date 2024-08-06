package ms.hispam.budget.util;


import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.ParameterDTO;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.countries.ConventArgDTO;
import ms.hispam.budget.dto.projections.ParameterProjection;
import ms.hispam.budget.entity.mysql.ConventArg;
import ms.hispam.budget.repository.mysql.ConventArgRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "ParameterMapperUtil")
public class ParameterMapperUtil {
    private final ConventArgRepository conventArgRepository;

    public ParameterMapperUtil(ConventArgRepository conventArgRepository) {
        this.conventArgRepository = conventArgRepository;
    }

    public ParametersDTO toDTO(ParameterProjection projection) {
        if (projection == null) {
            return null;
        }

        ParameterDTO parameterDTO = ParameterDTO.builder()
                .id(projection.getId())
                .name(projection.getVparameter())
                .description(projection.getDescription())
                .typeValor(projection.getTvalor())
                .build();
        //log.info("convenio: {}", projection.getConvenio());
        //log.info("areaPersonal: {}", projection.getArea());
        ConventArg conventArg = conventArgRepository.findByConvenioAndAreaPersonal(projection.getConvenio(), projection.getArea());
        ConventArgDTO conventArgDTO = null;
        if (conventArg != null) {
            conventArgDTO = ConventArgDTO.builder()
                    .id(conventArg.getId())
                    .convenio(conventArg.getConvenio())
                    .areaPersonal(conventArg.getAreaPersonal())
                    .build();
        }

        //log.error("ConventArg: {}", conventArg);
        return ParametersDTO.builder()
                .id(projection.getId())
                .description(projection.getDescription())
                .vparameter(projection.getVparameter())
                .allPeriod(projection.getAllperiod())
                .inPeriod(projection.getInperiod())
                .restringed(false)
                .isRetroactive(projection.getRetroactive())
                .parameter(parameterDTO)
                .value(projection.getTvalor().doubleValue())
                .range(Boolean.TRUE.equals(projection.getVrange()) ? "yes" : "no")
                .type(projection.getTypec())
                .period(null) // Asigna el valor correspondiente si está disponible en projection
                .periodRetroactive(null) // Asigna el valor correspondiente si está disponible en projection
                .order(null) // Asigna el valor correspondiente si está disponible en projection
                .conventArg(conventArgDTO)
                .vname(projection.getVname())
                .vrange(projection.getVrange())
                .build();
    }

    public List<ParametersDTO> toDTOList(List<ParameterProjection> projections) {
        return projections.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}