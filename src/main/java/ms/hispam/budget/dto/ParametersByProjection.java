package ms.hispam.budget.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import ms.hispam.budget.config.NominaPaymentLinksDeserializer;
import ms.hispam.budget.entity.mysql.*;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParametersByProjection {
    @NotEmpty(message="Se requiere bu")
    private String bu;
    @NotNull(message = "Se rquiere id Bu")
    private Integer idBu;
    @NotEmpty(message="Se requiere periodo")
    private String period;
    @Min(1)
    @NotNull(message = "Se rquiere id rango")
    private Integer range;
    private List<ParametersDTO> parameters;
    private List<PaymentComponentType> paymentComponent;
    private List<RangeBuDTO> temporalParameters;
    private List<Convenio> convenios;
    private List<ConvenioBono> convenioBonos;
    private List<ParameterDefault> defaultParameters;
    @NotEmpty(message="Se requiere nomina From")
    private String nominaFrom;
    @NotEmpty(message="Se requiere nomina to")
    private String nominaTo;
    private BaseExternResponse baseExtern;
    private BaseExternResponse bc;
    private String current;
    private List<PositionBaselineDTO> disabledPo;
    private Boolean viewPo;
    @JsonDeserialize(using = NominaPaymentLinksDeserializer.class)
    private Map<Integer, List<NominaPaymentComponentLink>> nominaPaymentLinks;
    private List<SeniorityAndQuinquennium> seniorityAndQuinquenniums;
    private List<EmployeeClassification> employeeClassifications;
    private List<ConceptoPresupuestal> conceptoPresupuestals;
}
