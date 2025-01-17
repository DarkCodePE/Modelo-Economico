package ms.hispam.budget.util;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
class GroupKey {
    String cuentaSap;
    String actividadFuncional;
    String ceCo;
    String concepto;
    String month;
    String po;  // Añadimos la posición (ID_PO)
    String idSsff;  // Añadimos ID_SSFF

    public GroupKey(String cuentaSap, String actividadFuncional, String ceCo, String concepto, String month, String po, String idSsff) {
        this.cuentaSap = cuentaSap;
        this.actividadFuncional = actividadFuncional;
        this.ceCo = ceCo;
        this.concepto = concepto;
        this.month = month;
        this.po = po;
        this.idSsff = idSsff;
    }

    public GroupKey(String account, String areaFuncional, String cCostos, String paymentComponent, String month) {
this.cuentaSap = account;
        this.actividadFuncional = areaFuncional;
        this.ceCo = cCostos;
        this.concepto = paymentComponent;
        this.month = month;
    }

    // Métodos hashCode y equals para que funcione correctamente en un HashMap.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GroupKey groupKey = (GroupKey) o;

        if (!Objects.equals(cuentaSap, groupKey.cuentaSap)) return false;
        if (!Objects.equals(actividadFuncional, groupKey.actividadFuncional)) return false;
        if (!Objects.equals(ceCo, groupKey.ceCo)) return false;
        if (!Objects.equals(month, groupKey.month)) return false;
        if (!Objects.equals(po, groupKey.po)) return false;
        return Objects.equals(concepto, groupKey.concepto);
    }

    @Override
    public int hashCode() {
        int result = cuentaSap.hashCode();
        result = 31 * result + (actividadFuncional != null ? actividadFuncional.hashCode() : 0);
        result = 31 * result + (ceCo != null ? ceCo.hashCode() : 0);
        result = 31 * result + (concepto != null ? concepto.hashCode() : 0);
        result = 31 * result + (month != null ? month.hashCode() : 0);
        result = 31 * result + (po != null ? po.hashCode() : 0);
        result = 31 * result + (idSsff != null ? idSsff.hashCode() : 0);
        return result;
    }
    // Considera implementar toString si necesitas imprimir la clave para depuración.
}
