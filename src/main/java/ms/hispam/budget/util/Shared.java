package ms.hispam.budget.util;

import ms.hispam.budget.dto.MonthProjection;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Shared {



    public static List<MonthProjection> generateMonthProjection(String monthBase, int range, Double amount) {
        List<MonthProjection> dates = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth fechaActual = YearMonth.parse(monthBase, formatter).plusMonths(1);
        for (int i = 0; i < range; i++) {
            dates.add( MonthProjection.builder().month( fechaActual.format(formatter)).amount(amount).build() );
            fechaActual = fechaActual.plusMonths(1);
        }

        return dates;
    }
    public static List<String> generateRangeMonth(String monthBase, int range) {
        List<String> dates = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        DateTimeFormatter formatterMonthName = DateTimeFormatter.ofPattern("MMM uuuu");
        YearMonth fechaActual = YearMonth.parse(monthBase, formatter).plusMonths(1);
        for (int i = 0; i < range; i++) {
            dates.add( fechaActual.format(formatterMonthName) );
            fechaActual = fechaActual.plusMonths(1);
        }

        return dates;
    }
    public static String nameMonth(String monthBase) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        DateTimeFormatter formatterMonthName = DateTimeFormatter.ofPattern("MMM uuuu");
        YearMonth fechaActual = YearMonth.parse(monthBase, formatter);

return fechaActual.format(formatterMonthName);

    }

    public static int compare(String date1, String date2) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        if(date1==""){date1="290001";};
        if(date2==""){date2="290001";};

        YearMonth yearMonth1 = YearMonth.parse(date1, formatter);
        YearMonth yearMonth2 = YearMonth.parse(date2, formatter);

        return yearMonth1.compareTo(yearMonth2);
    }

    public static Integer getIndex(List<String> months , String filter){
        return months.stream().filter(p->p.equalsIgnoreCase(filter)
        ).mapToInt(months::indexOf).findFirst().orElse(-1);
    }

    public static boolean verificarMesEnRango(String rango, String mes) {
        String[] partesRango = rango.split("-");
        String mesInicioStr = partesRango[0];
        String mesFinStr = partesRango[1];

        YearMonth mesInicio = YearMonth.parse(mesInicioStr, DateTimeFormatter.ofPattern("yyyyMM"));
        YearMonth mesFin = YearMonth.parse(mesFinStr, DateTimeFormatter.ofPattern("yyyyMM"));
        YearMonth mesVerificar = YearMonth.parse(mes, DateTimeFormatter.ofPattern("yyyyMM"));

        return !mesVerificar.isBefore(mesInicio) && !mesVerificar.isAfter(mesFin);
    }

    public static Double getDoubleWithDecimal(Double value){
        return Math.round(value * 100d) / 100d;
    }
}
