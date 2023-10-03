package ms.hispam.budget.dto.projections;

import java.nio.DoubleBuffer;

public interface CostPoProjection {

    public String getPosicion();
    public String getComponent();
    public String getVname();
    public Double getImporte();
    public String getMoneda();
    public String getFrecuencia();
}
