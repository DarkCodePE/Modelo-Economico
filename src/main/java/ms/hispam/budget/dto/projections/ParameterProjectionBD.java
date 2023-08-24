package ms.hispam.budget.dto.projections;

public interface ParameterProjectionBD {

    public Integer getId();
    public Integer getTvalor();
    public String getVperiod();
    public Integer getVtype();
    public Double getValue();
    public Boolean getVisretroactive();
    public String getVperiodretroactive();
    public String getVrange();
    public String getHperiod();
    public Integer getHrange();
    public String getVbu();

    public String getName();
    public String getDescription();
    public String getNfrom();
    public String getNto();


}
