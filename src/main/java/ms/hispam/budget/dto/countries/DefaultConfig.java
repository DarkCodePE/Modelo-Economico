package ms.hispam.budget.dto.countries;

import lombok.*;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.entity.mysql.*;
import ms.hispam.budget.dto.projections.ParameterProjection;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DefaultConfig extends Config {
    private List<ParameterProjection> parameters;

    @Builder
    public DefaultConfig(List<ComponentProjection> components, List<OperationResponse> baseExtern, List<CodeNomina> nominas, List<ParameterDefault> vDefault, List<RangeBuDTO> vTemporal, List<Convenio> convenios, List<ConvenioBono> convenioBonos, List<NominaPaymentComponentLink> nominaPaymentComponentRelations, List<EmployeeClassification> employeeClassifications, List<SeniorityAndQuinquennium> seniorityAndQuinquenniums, List<ConceptoPresupuestal> conceptoPresupuestals, List<ValidationRule> validationRules, List<ParameterGroup> parameterGroups, String money, String icon, Boolean vViewPo, String current, List<ParameterProjection> parameters) {
        super();
        setComponents(components);
        setBaseExtern(baseExtern);
        setNominas(nominas);
        setVDefault(vDefault);
        setVTemporal(vTemporal);
        setConvenios(convenios);
        setConvenioBonos(convenioBonos);
        setNominaPaymentComponentRelations(nominaPaymentComponentRelations);
        setEmployeeClassifications(employeeClassifications);
        setSeniorityAndQuinquenniums(seniorityAndQuinquenniums);
        setConceptoPresupuestals(conceptoPresupuestals);
        setValidationRules(validationRules);
        setParameterGroups(parameterGroups);
        setMoney(money);
        setIcon(icon);
        setVViewPo(vViewPo);
        setCurrent(current);
        this.parameters = parameters;
    }

    @Override
    public List<ParameterProjection> getParameters() {
        return parameters;
    }

    @Override
    public void setParameters(List<?> parameters) {
        this.parameters = (List<ParameterProjection>) parameters;
    }
}