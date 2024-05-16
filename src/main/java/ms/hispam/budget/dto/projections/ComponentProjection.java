package ms.hispam.budget.dto.projections;

public interface ComponentProjection {
    public Integer getId();
    public Integer getType();
    public String getComponent();
    public String getName();
    public Boolean getIscomponent();
    public Boolean getShow();
    public Integer getTvalor();
    public Boolean getIsBase();
    //isadditional
    public Boolean getIsAdditional();
}
