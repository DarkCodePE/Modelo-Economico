package ms.hispam.budget.entity.mysql;

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
@Table(name = "po_historial_extern")
public class PoHistorialExtern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String po;
    @Column(name = "base_extern")
    private String baseExtern;
    private String nvalue;
    @Column(name = "id_historial")
    private Integer idHistorial;
}
