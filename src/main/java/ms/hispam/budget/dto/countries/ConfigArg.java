package ms.hispam.budget.dto.countries;

import lombok.*;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.entity.mysql.*;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class ConfigArg extends Config {
    private List<ParametersDTO> parameters;
    private List<ParameterDefaultDTO> vDefault;
    @Builder
    public ConfigArg(List<ComponentProjection> components, List<OperationResponse> baseExtern, List<CodeNomina> nominas, List<ParameterDefaultDTO> vDefault, List<RangeBuDTO> vTemporal, List<Convenio> convenios, List<ConvenioBono> convenioBonos, List<NominaPaymentComponentLink> nominaPaymentComponentRelations, List<EmployeeClassification> employeeClassifications, List<SeniorityAndQuinquennium> seniorityAndQuinquenniums, List<ConceptoPresupuestal> conceptoPresupuestals, List<ValidationRule> validationRules, List<ParameterGroup> parameterGroups, String money, String icon, Boolean vViewPo, String current, List<ParametersDTO> parameters, List<ConventArgDTO> conventArgs) {
        super();
        setComponents(components);
        setBaseExtern(baseExtern);
        setNominas(nominas);
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
        setConventArgs(conventArgs);
        this.parameters = parameters;
        this.vDefault = vDefault;
    }

    @Override
    public List<ParametersDTO> getParameters() {
        return parameters;
    }

    @Override
    public void setParameters(List<?> parameters) {
        this.parameters = (List<ParametersDTO>) parameters;
    }

    @Override
    public List<ParameterDefaultDTO> getVDefault() {
        return vDefault;
    }

    @Override
    public void setVDefault(List<?> vDefault) {
        this.vDefault = (List<ParameterDefaultDTO>) vDefault;
    }
}