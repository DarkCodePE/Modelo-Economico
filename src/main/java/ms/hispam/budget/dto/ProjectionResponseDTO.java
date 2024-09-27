package ms.hispam.budget.dto;

public class ProjectionResponseDTO {
    private ProjectionSecondDTO projection;
    private String hash;

    // Constructor
    public ProjectionResponseDTO(ProjectionSecondDTO projection, String hash) {
        this.projection = projection;
        this.hash = hash;
    }

    // Getters and setters
    public ProjectionSecondDTO getProjection() {
        return projection;
    }

    public void setProjection(ProjectionSecondDTO projection) {
        this.projection = projection;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
