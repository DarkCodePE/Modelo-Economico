package ms.hispam.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HeadcountHistoricalProjectionDTO {
    private String position;
    private String poname;
    private String idssff;
    private String entitylegal;
    private String gender;
    private String bu;
    private String wk;
    private String division;
    private String department;
    private String component;
    private Double amount;
    private String classemp;
    private Date fnac;
    private Date fcontra;
    private String af;
    private String divisionname;
    private String cc;

    // Incluye los getters y setters aquí

    @Override
    public String toString() {
        return "HeadcountHistoricalProjectionDTO{" +
                "position='" + position + '\'' +
                ", poname='" + poname + '\'' +
                ", idssff='" + idssff + '\'' +
                ", entitylegal='" + entitylegal + '\'' +
                ", gender='" + gender + '\'' +
                ", bu='" + bu + '\'' +
                ", wk='" + wk + '\'' +
                ", division='" + division + '\'' +
                ", department='" + department + '\'' +
                ", component='" + component + '\'' +
                ", amount=" + amount +
                ", classemp='" + classemp + '\'' +
                ", fnac=" + fnac +
                ", fcontra=" + fcontra +
                ", af='" + af + '\'' +
                ", divisionName='" + divisionname + '\'' +
                ", cc='" + cc + '\'' +
                '}';
    }
}