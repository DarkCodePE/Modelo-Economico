package ms.hispam.budget.entity.mysql;

import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "type_employee_bu_projection")
public class TypeEmployeeProjection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer bu;
    @Column(name = "type_employee")
    private String typeEmployee;
}
