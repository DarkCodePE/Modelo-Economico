package ms.hispam.budget.util;

public class DaysVacationInfo {
    private int lowerLimit;
    private int upperLimit;
    private int vacationDays;

    public DaysVacationInfo(int lowerLimit, int upperLimit, int vacationDays) {
        this.lowerLimit = lowerLimit;
        this.upperLimit = upperLimit;
        this.vacationDays = vacationDays;
    }

    public int getLowerLimit() {
        return lowerLimit;
    }

    public int getUpperLimit() {
        return upperLimit;
    }

    public int getVacationDays() {
        return vacationDays;
    }
}
