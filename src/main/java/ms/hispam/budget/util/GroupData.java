package ms.hispam.budget.util;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class GroupData {
    List<String> meses;
    Map<String, Double> montoPorMes;
    Double sum;

    public GroupData(List<String> meses, Map<String, Double> montoPorMes, Double sum) {
        this.meses = meses;
        this.montoPorMes = montoPorMes;
        this.sum = sum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GroupData groupData = (GroupData) o;

        if (!meses.equals(groupData.meses)) return false;
        if (!montoPorMes.equals(groupData.montoPorMes)) return false;
        return sum.equals(groupData.sum);
    }

    @Override
    public int hashCode() {
        int result = meses.hashCode();
        result = 31 * result + montoPorMes.hashCode();
        result = 31 * result + sum.hashCode();
        return result;
    }
}
