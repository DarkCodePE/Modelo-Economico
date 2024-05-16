package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "convenio_bono_historial")
@NoArgsConstructor
@Getter
@Setter
public class ConvenioBonoHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "nivel")
    private String nivel;

    @Column(name = "porcentaje")
    private double porcentaje;

    @ManyToOne
    @JoinColumn(name = "id_historial", referencedColumnName = "id")
    private HistorialProjection historialProjection;

    // Getters and setters...
}
