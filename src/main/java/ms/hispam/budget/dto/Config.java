package ms.hispam.budget.dto;

import lombok.*;
import ms.hispam.budget.dto.countries.ConventArgDTO;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.dto.projections.ParameterProjection;
import ms.hispam.budget.entity.mysql.*;

import java.util.List;
@Getter
@Setter
public abstract class Config {
    private List<ComponentProjection> components;
    private List<?> parameters;
    private List<OperationResponse> baseExtern;
    private List<CodeNomina> nominas;
    private List<?> vDefault;
    private List<RangeBuDTO> vTemporal;
    private List<Convenio> convenios;
    private List<ConvenioBono> convenioBonos;
    private List<NominaPaymentComponentLink> nominaPaymentComponentRelations;
    private List<EmployeeClassification> employeeClassifications;
    private List<SeniorityAndQuinquennium> seniorityAndQuinquenniums;
    private List<ConceptoPresupuestal> conceptoPresupuestals;
    private List<EdadSV> edadSV;
    private List<ValidationRule> validationRules;
    private List<ParameterGroup> parameterGroups;
    private List<ConventArgDTO> conventArgs;
    private List<NominaPaymentComponentLink> nominaPaymentComponentLinks;
    private String money;
    private String icon;
    private Boolean vViewPo;
    private String current;
}
