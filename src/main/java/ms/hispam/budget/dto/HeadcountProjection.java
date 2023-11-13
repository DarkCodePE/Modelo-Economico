package ms.hispam.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

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
    private Date fNac;
    private Date fContra;
}
