package ms.hispam.budget.util;

import ms.hispam.budget.dto.ParametersByProjection;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class ProjectionUtils {

    public static class ProjectionYearRange {
        private final int startYear;
        private final int endYear;

        public ProjectionYearRange(int startYear, int endYear) {
            this.startYear = startYear;
            this.endYear = endYear;
        }

        public int getStartYear() {
            return startYear;
        }

        public int getEndYear() {
            return endYear;
        }

        public boolean includesYear(int year) {
            return year >= startYear && year <= endYear;
        }
    }

    public static ProjectionYearRange determineProjectionYearRange(ParametersByProjection projection) {
        LocalDate startDate = parseDate(projection.getNominaFrom());
        LocalDate endDate = parseDate(projection.getNominaTo());

        if (startDate == null || endDate == null) {
            // Fallback en caso de que no se puedan parsear las fechas
            int currentYear = LocalDate.now().getYear();
            return new ProjectionYearRange(currentYear, currentYear);
        }

        int startYear = startDate.getYear();
        int endYear = endDate.getYear();

        return new ProjectionYearRange(startYear, endYear);
    }

    private static LocalDate parseDate(String dateString) {
        try {
            return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy/MM"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}