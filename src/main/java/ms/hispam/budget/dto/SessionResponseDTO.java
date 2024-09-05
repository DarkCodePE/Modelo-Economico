package ms.hispam.budget.dto;

import lombok.Data;

@Data
public class SessionResponseDTO {
    private String sessionId;

    public SessionResponseDTO(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}