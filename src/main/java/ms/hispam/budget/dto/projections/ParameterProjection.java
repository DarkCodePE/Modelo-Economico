package ms.hispam.budget.dto.projections;

public interface ParameterProjection {

    public Integer getId();
    public String getVparameter();
    public Integer getTvalor();
    public String getDescription();
    public Integer getTypec();
    public Boolean getRetroactive();
    public Boolean getVrange();
    public Boolean getAllperiod();
    public String getVname();
    public Boolean getInperiod();
    public String getRestringed();

}
