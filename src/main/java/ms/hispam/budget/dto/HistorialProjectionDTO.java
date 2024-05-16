package ms.hispam.budget.dto;

import lombok.*;
import ms.hispam.budget.entity.mysql.Convenio;
import ms.hispam.budget.entity.mysql.ConvenioBono;
import ms.hispam.budget.entity.mysql.ConvenioBonoHistorial;
import ms.hispam.budget.entity.mysql.ConvenioHistorial;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialProjectionDTO {

    private Integer id;
    private String bu;
    private String name;
    private Date vDate;
    private Integer vRange;
    private String vPeriod;
    private Date createdAt;
    private Boolean isTop;
    private Integer idBu;
    private BaseExternResponse baseExtern;
    private BaseExternResponse bc;
    private List<RangeBuDTO> temporalParameters;
    private List<ConvenioDTO> convenio;
    private List<ConvenioBonoDTO> convenioBono;
}
