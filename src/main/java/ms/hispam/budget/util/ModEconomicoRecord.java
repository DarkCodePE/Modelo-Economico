package ms.hispam.budget.util;

import java.time.LocalDateTime;

// Record para almacenar los datos
public record ModEconomicoRecord(
        LocalDateTime fechaEjecucion,
        String usuarioEjecuta,
        LocalDateTime fechaTraspaso,
        String usuarioTraspaso,
        long qRegistros,
        double importeTotal,
        String pais,
        String proyeccion,
        String proyeccionNombre,
        int periodoEjecucion,
        String positionId,
        String idSsff,
        String actividadFuncional,
        String ceco,
        String concepto,
        String cuentaSap,
        int mes,
        double monto
) {}
