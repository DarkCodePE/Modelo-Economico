package ms.hispam.budget.util;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

// Clase auxiliar para el batch insert
public class ModEconomicoBatchPreparedStatementSetter implements BatchPreparedStatementSetter {
    private final List<ModEconomicoRecord> records;

    public ModEconomicoBatchPreparedStatementSetter(List<ModEconomicoRecord> records) {
        this.records = records;
    }

    @Override
    public void setValues(PreparedStatement ps, int i) throws SQLException {
        ModEconomicoRecord record = records.get(i);
        ps.setTimestamp(1, Timestamp.valueOf(record.fechaEjecucion()));
        ps.setString(2, record.usuarioEjecuta());
        ps.setTimestamp(3, Timestamp.valueOf(record.fechaTraspaso()));
        ps.setString(4, record.usuarioTraspaso());
        ps.setLong(5, record.qRegistros());
        ps.setDouble(6, record.importeTotal());
        ps.setString(7, record.pais());
        ps.setString(8, record.proyeccion());
        ps.setString(9, record.proyeccionNombre());
        ps.setInt(10, record.periodoEjecucion());
        ps.setString(11, record.positionId());
        ps.setString(12, record.idSsff());
        ps.setString(13, record.actividadFuncional());
        ps.setString(14, record.ceco());
        ps.setString(15, record.concepto());
        ps.setString(16, record.cuentaSap());
        ps.setInt(17, record.mes());
        ps.setDouble(18, record.monto());
    }

    @Override
    public int getBatchSize() {
        return records.size();
    }
}
