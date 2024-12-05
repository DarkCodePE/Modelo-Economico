package ms.hispam.budget.repository.sqlserver;

import ms.hispam.budget.dto.ComponentNominaDTO;
import ms.hispam.budget.dto.ComponentNominaProjectionImpl;
import ms.hispam.budget.dto.NominaProjection;
import ms.hispam.budget.dto.projections.ComponentNominaProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.repository.mysql.BuRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.persistence.EntityManager;
import javax.persistence.ParameterMode;
import javax.persistence.PersistenceContext;
import javax.persistence.StoredProcedureQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


public class ParametersRepositoryImpl implements CustomParametersRepository {

    @PersistenceContext(unitName = "sqlServerPU")
    private EntityManager entityManager;


    private final BuRepository buRepository;

    public ParametersRepositoryImpl(@Qualifier("mysqlBuRepository") BuRepository buRepository) {
        this.buRepository = buRepository;
    }

    @Override
    public List<ComponentNominaProjection> getComponentNominaByBu(
            String buName,
            String password,
            String buParam,
            String pIni,
            String pFin,
            String coNomina
    ) {
        // Obtener la entidad BU para obtener el país
        Optional<Bu> buEntityOpt = buRepository.findByBu(buName);
        if (!buEntityOpt.isPresent()) {
            throw new IllegalArgumentException("BU inválido: " + buName);
        }

        Bu buEntity = buEntityOpt.get();
        String countryName = extractCountryNameFromBu(buEntity);

        // Determinar el nombre del procedimiento almacenado
        String procedureName;
        if (countryName != null) {
            procedureName = "get_nom_ppto_" + countryName;
        } else {
            procedureName = "get_nom_ppto"; // Procedimiento genérico
        }

        // Crear la consulta al procedimiento almacenado
        StoredProcedureQuery query = entityManager.createStoredProcedureQuery(procedureName);

        // Registrar los parámetros
        query.registerStoredProcedureParameter("password", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("bu", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_ini", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_fin", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("co_nomina", String.class, ParameterMode.IN);

        // Establecer los valores de los parámetros
        query.setParameter("password", password);
        query.setParameter("bu", buParam);
        query.setParameter("p_ini", pIni);
        query.setParameter("p_fin", pFin);
        query.setParameter("co_nomina", coNomina);

        // Ejecutar el procedimiento almacenado y obtener los resultados
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        // Mapear los resultados a ComponentNominaProjectionImpl
        List<ComponentNominaProjection> componentNominaProjections = new ArrayList<>();
        //TODO:  NumberFormatException For input string: "UYU" IN ROW[2] - Java.lang.Integer cannot be cast to class java.lang.Double row[3] puede ser tanto un Integer como un Double
        for (Object[] row : results) {
            ComponentNominaProjection projection = new ComponentNominaProjectionImpl(
                    (String) row[0], // ID_SSFF
                    (String) row[1], // CodigoNomina
                    ((Number) row[3]).doubleValue(), // Importe
                    ((Number) row[4]).doubleValue()  // Q_Dias_Horas
            );
            componentNominaProjections.add(projection);
        }

        return componentNominaProjections;
    }

    /**
     * Método para extraer el nombre del país desde el BU.
     * Asume que el nombre del BU comienza con "T. " seguido del nombre del país.
     * Ejemplo: "T. URUGUAY" -> "Uruguay"
     * @param buEntity Entidad BU
     * @return Nombre del país
     */
    private String extractCountryNameFromBu(Bu buEntity) {
        String buName = buEntity.getBu();
        if (buName.startsWith("T. ")) {
            String countryPart = buName.substring(3).trim();
            // Puedes agregar lógica adicional si es necesario
            return mapBuToCountryName(countryPart);
        } else {
            return null;
        }
    }

    /**
     * Mapea la parte del BU al nombre de país correspondiente.
     * @param countryPart Parte del BU que representa al país
     * @return Nombre del país
     */
    private String mapBuToCountryName(String countryPart) {
        return switch (countryPart.toUpperCase()) {
            case "URU", "URUGUAY" -> "Uruguay";
            //case "PERU" -> "Peru";
            //case "ECUADOR" -> "Ecuador";
            //case "COLOMBIA" -> "Colombia";
            //case "MEXICO" -> "Mexico";
            //case "ARGENTINA" -> "Argentina";
            // Agrega más casos según tus necesidades
            default -> null; // No hay mapeo, usar procedimiento genérico
        };
    }
}
