package ms.hispam.budget.cache;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.Map;

import static ms.hispam.budget.cache.SimpleCache.TYPEMONTH;

public class DateCache {
    private final int maxSize;
    private final LinkedHashMap<LocalDate, Double> cache;

    public DateCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<LocalDate, Double> eldest) {
                return size() > maxSize;
            }
        };
    }

    public void put(LocalDate date, double value) {
        cache.put(date, value);
    }

    public Double get(LocalDate date) {
        return cache.get(date);
    }

    public void clear() {
        cache.clear();
    }

    public Double getUltimoValorEnRango(String fechaInicio, String fechaFin) {
        LocalDate fechaInicioLocalDate = formatStringToLocalDate(fechaInicio);
        LocalDate fechaFinLocalDate = formatStringToLocalDate(fechaFin);
        for (Map.Entry<LocalDate, Double> entry : cache.entrySet()) {
            LocalDate date = entry.getKey();
            if (date.isAfter(fechaInicioLocalDate) && date.isBefore(fechaFinLocalDate)) {
                // La fecha está en el rango, actualiza el valor
                return entry.getValue();
            }
            if (date.isEqual(fechaInicioLocalDate) || date.isEqual(fechaFinLocalDate)) {
                // La fecha es igual a la fecha de inicio o fin, actualiza el valor
                return entry.getValue();
            }
        }
        return null; // Ningún valor encontrado en el rango
    }
    public static LocalDate formatStringToLocalDate(String period){
        DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                .appendPattern(TYPEMONTH)
                .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                .toFormatter();
        return  LocalDate.parse(period, dateFormat);
    }
}
