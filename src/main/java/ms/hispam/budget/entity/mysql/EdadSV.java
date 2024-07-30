package ms.hispam.budget.entity.mysql;

import javax.persistence.*;

@Entity
@Table(name = "edad_sv")
public class EdadSV {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "edad_inicio")
    private int edadInicio;
    @Column(name = "edad_fin")
    private int edadFin;
    @Column(name = "porcentaje_sv_ley")
    private double porcentajeSvLey;

    public EdadSV() {
    }

    public EdadSV(int edadInicio, int edadFin, double porcentajeSvLey) {
        this.edadInicio = edadInicio;
        this.edadFin = edadFin;
        this.porcentajeSvLey = porcentajeSvLey;
    }

    // Getters y setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getEdadInicio() {
        return edadInicio;
    }

    public void setEdadInicio(int edadInicio) {
        this.edadInicio = edadInicio;
    }

    public int getEdadFin() {
        return edadFin;
    }

    public void setEdadFin(int edadFin) {
        this.edadFin = edadFin;
    }

    public double getPorcentajeSvLey() {
        return porcentajeSvLey;
    }

    public void setPorcentajeSvLey(double porcentajeSvLey) {
        this.porcentajeSvLey = porcentajeSvLey;
    }
}