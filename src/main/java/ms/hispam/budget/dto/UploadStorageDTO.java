package ms.hispam.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UploadStorageDTO {

    private Boolean status;
    private String fileName;
    private String url;
    private String message;
}

