package ms.hispam.budget.entity.sqlserver;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "T_ParametrosGlobal")
public class ParametrosGlobal {
    @Id
    private Integer id;
    // Asegúrate de tener la propiedad 'bu' si es necesaria
    private String bu;
    private String llave;
    private String periodo;
    private String descripcion;

    private Float valor;
}
