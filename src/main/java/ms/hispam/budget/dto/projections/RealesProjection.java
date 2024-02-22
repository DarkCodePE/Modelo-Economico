package ms.hispam.budget.dto.projections;

import java.math.BigDecimal;

public interface RealesProjection {

    public String getPeriodo();
    public String getPeriodoMensual();
    public String getCuentaSAP();
    public BigDecimal getAmount();
}
