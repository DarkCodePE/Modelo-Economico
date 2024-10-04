package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "projection_history")
@NoArgsConstructor
@Getter
@Setter
@Builder
@AllArgsConstructor
public class ProjectionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String parameters;

    private String fileUrl;

    private LocalDateTime createdAt;

    private String hash;

    private String reportName;
}
