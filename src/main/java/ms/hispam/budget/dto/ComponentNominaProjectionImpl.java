package ms.hispam.budget.dto;

import ms.hispam.budget.dto.projections.ComponentNominaProjection;

public class ComponentNominaProjectionImpl implements ComponentNominaProjection {
    private String ID_SSFF;
    private String CodigoNomina;
    private Double Importe;
    private Double Q_Dias_Horas;

    // Constructor
    public ComponentNominaProjectionImpl(String ID_SSFF, String CodigoNomina, Double Importe, Double Q_Dias_Horas) {
        this.ID_SSFF = ID_SSFF;
        this.CodigoNomina = CodigoNomina;
        this.Importe = Importe;
        this.Q_Dias_Horas = Q_Dias_Horas;
    }

    // Implementación de los métodos de la interfaz
    @Override
    public String getID_SSFF() {
        return ID_SSFF;
    }

    @Override
    public String getCodigoNomina() {
        return CodigoNomina;
    }

    @Override
    public Double getImporte() {
        return Importe;
    }

    @Override
    public Double getQ_Dias_Horas() {
        return Q_Dias_Horas;
    }

}
