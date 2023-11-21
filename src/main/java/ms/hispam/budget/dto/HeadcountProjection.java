package ms.hispam.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.sql.Date;
import java.time.LocalDate;


@Data
@AllArgsConstructor
@Builder
public class HeadcountProjection {

    private  String position;
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
    private String classEmp;
    private LocalDate fNac;
    private LocalDate fContra;
    private String areaFuncional;
    private String divisionName;
    private String cCostos;
}
