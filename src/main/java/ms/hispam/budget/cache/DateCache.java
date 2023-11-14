package ms.hispam.budget.cache;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static ms.hispam.budget.cache.SimpleCache.TYPEMONTH;

public class DateCache {
    private final int maxSize;
    private final ConcurrentHashMap<LocalDate, Double> cache;

    public DateCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>(maxSize, 0.75f);
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
            LocalDate entryDate = entry.getKey();
            if ((entryDate.isEqual(fechaInicioLocalDate) || entryDate.isEqual(fechaFinLocalDate))
                    || (entryDate.isAfter(fechaInicioLocalDate) && entryDate.isBefore(fechaFinLocalDate))) {
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
