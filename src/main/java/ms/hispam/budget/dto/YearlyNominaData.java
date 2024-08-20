package ms.hispam.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

// Asumimos que esta clase ya existe o la creamos para manejar la información del año
@Data
@AllArgsConstructor
@Builder
public class YearlyNominaData {
    private int year;
    private List<NominaProjection> nominas;
}