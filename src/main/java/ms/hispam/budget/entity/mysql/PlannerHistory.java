package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "planner_history")
public class PlannerHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String parameters;

    private String fileUrl;
    private LocalDateTime createdAt;
    private String hash;
    private String reportName;

    // Getters y setters
}