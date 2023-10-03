package ms.hispam.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class NominaProjection {

    private String idssff;
    private String codeNomina;
    private Double importe;
}
