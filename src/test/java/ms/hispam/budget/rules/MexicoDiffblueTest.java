package ms.hispam.budget.rules;

import static org.hibernate.validator.internal.util.Contracts.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParameterDTO;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.service.MexicoService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {Mexico.class})
@ExtendWith(SpringExtension.class)
@Slf4j(topic = "MexicoDiffblueTest")
class MexicoDiffblueTest {

    /*@Autowired
    private Mexico mexico;
>>>>>>> old_change_peru_v3


<<<<<<< HEAD
=======
    *//**
     * Method under test: {@link Mexico
     * #seguroDental(List, List, String, Integer)}
     *//*
    @Test
    void testSeguroDental() {
        // Arrange
        ArrayList<PaymentComponentDTO> component = new ArrayList<>();

        // Act and Assert
        assertThrows(RuntimeException.class, () -> mexico.seguroDental(component, new ArrayList<>(), "Period", 1, 0.0));
    }

    *//**
     * Method under test: {@link Mexico
     * #seguroDental(List, List, String, Integer)}
     *//*
    @Test
    void testSeguroDental2() {
        // Arrange
        ArrayList<PaymentComponentDTO> component = new ArrayList<>();
        PaymentComponentDTO.PaymentComponentDTOBuilder builderResult = PaymentComponentDTO.builder();
        PaymentComponentDTO.PaymentComponentDTOBuilder paymentComponentResult = builderResult.amount(new BigDecimal("2.3"))
                .amountString("10")
                .name("Name")
                .paymentComponent("Payment Component");
        PaymentComponentDTO buildResult = paymentComponentResult.projections(new ArrayList<>())
                .salaryType("Salary Type")
                .show(true)
                .type(1)
                .build();
        component.add(buildResult);

        // Act and Assert
        assertThrows(RuntimeException.class, () -> mexico.seguroDental(component, new ArrayList<>(), "Period", 1, 0.0));
    }

    *//**
     * Method under test: {@link Mexico
     * #seguroDental(List, List, String, Integer)}
     * Scenario: Calcular la participación de los trabajadores para un empleado
     * Given que estoy en la página de cálculo de la nómina después de ingresar los
     * detalles del empleado When selecciono calcular la participación de los
     * trabajadores Then la participación de los trabajadores debería calcularse
     * correctamente And debería poder continuar con el proceso de cálculo de la
     * nómina
     *//*
    @Test
    void deberiaCalcularSeguroDentalCorrectamenteCuandoSeProporcionanLosParametrosCorrectos() {
        // Preparar
        List<PaymentComponentDTO> component = new ArrayList<>();
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent("SALARY");
        salaryComponent.setAmount(BigDecimal.valueOf(1000.0));
        List<MonthProjection> projections = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            MonthProjection projection = new MonthProjection();
            projection.setMonth(Integer.toString(i + 1));
            projection.setAmount(BigDecimal.valueOf(1000.0 / 12));
            projections.add(projection);
        }
        salaryComponent.setProjections(projections);
        component.add(salaryComponent);

        List<ParametersDTO> parameters = new ArrayList<>();
        ParametersDTO seguroDentalParam = new ParametersDTO();
        seguroDentalParam.setParameter(new ParameterDTO(1, "Seguro Dental", "description", 1));
        seguroDentalParam.setValue(1000.0 * 0.1); // 10% para el seguro dental
        parameters.add(seguroDentalParam);

        String period = "202201";
        Integer range = 12;

        // Actuar
        double someDoubleValue = 0.0; // Reemplaza esto con el valor double que necesitas pasar
        mexico.seguroDental(component, parameters, "Period", 1, someDoubleValue);

        // Afirmar
        Optional<PaymentComponentDTO> seguroDentalComponentOpt = component.stream()
                .filter(c -> c.getPaymentComponent().equals("Seguro Dental"))
                .findFirst();

        assertTrue(seguroDentalComponentOpt.isPresent(), "Componente Seguro Dental no encontrado");

        PaymentComponentDTO seguroDentalComponent = seguroDentalComponentOpt.get();
        log.debug("seguroDentalComponent: {}", seguroDentalComponent);
        assertEquals(0, BigDecimal.valueOf(100.0).compareTo(seguroDentalComponent.getAmount()),
                "Cantidad incorrecta de Seguro Dental");

        List<MonthProjection> projectionsPerMoth = seguroDentalComponent.getProjections();
        log.debug("projections: {}", projections);
        assertEquals(range.intValue(), projectionsPerMoth.size(), "Número incorrecto de proyecciones");

        double totalSalaries = component.stream()
                .filter(c -> c.getPaymentComponent().equals("SALARY"))
                .mapToDouble(c -> c.getAmount().doubleValue())
                .sum();

        for (MonthProjection projection : projections) {
            double expectedAmount = (projection.getAmount().doubleValue() / totalSalaries) * seguroDentalParam.getValue();
            log.debug("expectedAmount: {}", expectedAmount);
            assertEquals(0, BigDecimal.valueOf(expectedAmount).compareTo(BigDecimal.valueOf(8.333333333333332)),
                    "Cantidad de seguro dental incorrecta");
        }
    }

    *//**
     * Method under test: {@link Mexico
     * #seguroVida(List, List, String, Integer)}
     * (List, List, String, Integer)}
     *//*

    *//*
     * Feature: Cálculo del seguro de vida en la nómina
     *
     * Scenario: Calcular el seguro de vida para un empleado Given que estoy en la
     * página de cálculo de la nómina después de ingresar los detalles del empleado
     * When selecciono calcular el seguro de vida Then el seguro de vida debería
     * calcularse correctamente And debería poder continuar con el proceso de
     * cálculo de la nómina
     *
     * Scenario: Actualizar el valor del seguro de vida Given que ya he calculado el
     * seguro de vida para un empleado When cambio el valor del seguro de vida y
     * recalculo la nómina Then la cantidad del seguro de vida en la nómina debería
     * actualizarse al nuevo valor And el valor anterior del seguro de vida debería
     * ser reemplazado por el nuevo valor
     *//*
    @Test
    void deberiaCalcularParticipacionTrabajadoresCorrectamenteCuandoSeProporcionanLosParametrosCorrectos() {
        // Preparar
        List<PaymentComponentDTO> component = new ArrayList<>();
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent("SALARY");
        salaryComponent.setAmount(BigDecimal.valueOf(1000.0));
        List<MonthProjection> projections = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            MonthProjection projection = new MonthProjection();
            projection.setMonth(Integer.toString(i + 1));
            projection.setAmount(BigDecimal.valueOf(1000.0 / 12));
            projections.add(projection);
        }
        salaryComponent.setProjections(projections);
        component.add(salaryComponent);

        List<ParametersDTO> parameters = new ArrayList<>();
        ParametersDTO participacionTrabajadoresParam = new ParametersDTO();
        participacionTrabajadoresParam
                .setParameter(new ParameterDTO(1, "Participacion de los trabajadores", "description", 1));
        participacionTrabajadoresParam.setValue(1000.0 * 0.1); // 10% para la participación de los trabajadores
        parameters.add(participacionTrabajadoresParam);

        String period = "202201";
        Integer range = 12;

        // Actuar
        mexico.participacionTrabajadores(component, new ArrayList<>(), parameters, period, range, 0.0);

        // Afirmar
        Optional<PaymentComponentDTO> participacionTrabajadoresComponentOpt = component.stream()
                .filter(c -> c.getPaymentComponent().equals("PARTICIPACION"))
                .findFirst();

        assertTrue(participacionTrabajadoresComponentOpt.isPresent(),
                "Componente Participacion de los trabajadores no encontrado");

        PaymentComponentDTO participacionTrabajadoresComponent = participacionTrabajadoresComponentOpt.get();
        assertEquals(0, BigDecimal.valueOf(100.0).compareTo(participacionTrabajadoresComponent.getAmount()),
                "Cantidad incorrecta de Participacion de los trabajadores");

        List<MonthProjection> projectionsPerMoth = participacionTrabajadoresComponent.getProjections();
        assertEquals(range.intValue(), projectionsPerMoth.size(), "Número incorrecto de proyecciones");

        double totalSalaries = component.stream()
                .filter(c -> c.getPaymentComponent().equals("SALARY"))
                .mapToDouble(c -> c.getAmount().doubleValue())
                .sum();

        for (MonthProjection projection : projections) {
            double expectedAmount = (projection.getAmount().doubleValue() / totalSalaries)
                    * participacionTrabajadoresParam.getValue();
            assertEquals(0, BigDecimal.valueOf(expectedAmount).compareTo(projection.getAmount()),
                    "Cantidad de participacion de los trabajadores incorrecta");
        }
    }

    *//**
     * Method under test:
     * {@link Mexico
     * #participacionTrabajadores(List, List, List, String, Integer)}
     *//*
    @Test
    @Disabled("TODO: Complete this test")
    void testParticipacionTrabajadores() {
        // TODO: Complete this test.
        //   Reason: R013 No inputs found that don't throw a trivial exception.
        //   Diffblue Cover tried to run the arrange/act section, but the method under
        //   test threw
        //   java.lang.NullPointerException: Cannot invoke "ms.hispam.budget.dto.PaymentComponentDTO.getProjections()" because "salaryComponent" is null
        //       at ms.hispam.budget.rules.Mexico.participacionTrabajadores(Mexico.java:346)
        //   See https://diff.blue/R013 to resolve this issue.

        // Arrange
        ArrayList<PaymentComponentDTO> component = new ArrayList<>();
        ArrayList<ParametersDTO> employeeParticipationList = new ArrayList<>();

        // Act
        mexico.participacionTrabajadores(component, employeeParticipationList, new ArrayList<>(), "Period", 1, 0.0);
    }
*/

}