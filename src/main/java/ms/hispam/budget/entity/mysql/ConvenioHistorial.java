package ms.hispam.budget.entity.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "convenio_historial")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConvenioHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "name")
    private String name;

    @Column(name = "retiroPercentage")
    private double retiroPercentage;

    @Column(name = "imssPercentage")
    private double imssPercentage;

    @Column(name = "infonavitPercentage")
    private double infonavitPercentage;

    @ManyToOne
    @JoinColumn(name = "id_historial", referencedColumnName = "id")
    private HistorialProjection historialProjection;

    // Getters and setters...
}
