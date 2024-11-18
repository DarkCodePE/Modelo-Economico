package ms.hispam.budget.entity.sqlserver;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "MODECONOMICO_Proceso")
public class ModEconomicoProceso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "indice")
    private Long id;

    @Column(name = "FechaEjecucionProyeccion")
    private LocalDateTime fechaEjecucionProyeccion;

    @Column(name = "UsuarioEjecuta")
    private String usuarioEjecuta;

    @Column(name = "FechaTraspaso")
    private LocalDateTime fechaTraspaso;

    @Column(name = "UsuarioTraspaso")
    private String usuarioTraspaso;

    @Column(name = "QRegistros")
    private Long qRegistros;

    @Column(name = "ImporteTotal")
    private Double importeTotal;

    @Column(name = "Pais")
    private String pais;

    @Column(name = "Proyeccion")
    private String proyeccion;

    @Column(name = "ProyeccionNombre")
    private String proyeccionNombre;

    @Column(name = "PeriodoEjecucion")
    private Integer periodoEjecucion;

    @Column(name = "PositionID")
    private String positionID;

    @Column(name = "ID_SSFF")
    private String idSSFF;

    @Column(name = "ActividadFuncional")
    private String actividadFuncional;

    @Column(name = "Ceco")
    private String ceco;

    @Column(name = "Concepto")
    private String concepto;

    @Column(name = "CuentaSAP")
    private String cuentaSAP;

    @Column(name = "Mes")
    private Integer mes;

    @Column(name = "Monto")
    private Double monto;
}
