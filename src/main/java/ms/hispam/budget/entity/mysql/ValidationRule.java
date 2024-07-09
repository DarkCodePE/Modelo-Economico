package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "validation_rules")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ValidationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "data_type", nullable = false)
    private String dataType;

    @Column(name = "is_required", nullable = false)
    private boolean isRequired;

    @Column(name = "validation_message", nullable = false)
    private String validationMessage;

    @Column(name = "error_level", nullable = false)
    private String errorLevel;

    @ManyToOne
    @JoinColumn(name = "bu", nullable = false)
    private BaseExtern bu;
}