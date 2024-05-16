package ms.hispam.budget.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ConvenioDTO {
    private int id;
    private String convenioName;
    private double retiroPercentage;
    private double imssPercentage;
    private double infonavitPercentage;
}
