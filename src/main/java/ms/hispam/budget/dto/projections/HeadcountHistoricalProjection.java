package ms.hispam.budget.dto.projections;


import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
public interface HeadcountHistoricalProjection {
    public String getPosition();
    public String getPoname();
    public String getIdssff();
    public String getEntitylegal();
    public String getGender();
    public String getBu();
    public String getWk();
    public String getDivision();
    public String getDepartment();
    public String getComponent();
    public Double getAmount();
    public String getClassemp();
    public Date getFnac();
    public Date getFcontra();
    public String getAf();
    public String getDivisionname();
    public String getCc();

    default Optional<LocalDate> getFnacAsLocalDate() {
        return Optional.ofNullable(getFnac()).map(Date::toLocalDate);
    }

    default Optional<LocalDate> getFcontraAsLocalDate() {
        return Optional.ofNullable(getFcontra()).map(Date::toLocalDate);
    }
    //convent
    public String getConvent();
    public String getLevel();
}
