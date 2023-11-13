package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "days_vacation_of_time")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DaysVacationOfTime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "range_of_time")
    private String range;
    private Integer vacationDays;
}
