package ms.hispam.budget.entity.mysql;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(name = "employee_classification")
public class EmployeeClassification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String category;
    @Column(name = "type_emp")
    private String typeEmp;
    @Column(name = "value_allowance")
    private Double valueAllowance;
}
