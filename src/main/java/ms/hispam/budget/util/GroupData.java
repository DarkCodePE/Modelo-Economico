package ms.hispam.budget.util;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GroupData {
    List<String> meses;
    Double sum;

    public GroupData(List<String> meses, Double sum) {
        this.meses = meses;
        this.sum = sum;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GroupData groupData = (GroupData) o;

        if (!meses.equals(groupData.meses)) return false;
        return sum.equals(groupData.sum);
    }

    @Override
    public int hashCode() {
        int result = meses.hashCode();
        result = 31 * result + sum.hashCode();
        return result;
    }
}
