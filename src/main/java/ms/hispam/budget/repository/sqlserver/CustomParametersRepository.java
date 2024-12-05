package ms.hispam.budget.repository.sqlserver;

import ms.hispam.budget.dto.projections.ComponentNominaProjection;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

@NoRepositoryBean
public interface CustomParametersRepository {
    List<ComponentNominaProjection> getComponentNominaByBu(
            String buName,
            String password,
            String buParam,
            String pIni,
            String pFin,
            String coNomina
    );
}