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
/**
 * @Lob: Indica que el campo debe ser tratado como un objeto grande (Large Object). En este caso, LONGTEXT es apropiado para almacenar grandes cantidades de texto.
 * @LONGTEXT permite almacenar hasta 4,294,967,295 caracteres
 */
public class ProjectionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String parameters;

    private String fileUrl;

    private LocalDateTime createdAt;

    private String hash;

    private String reportName;
}
