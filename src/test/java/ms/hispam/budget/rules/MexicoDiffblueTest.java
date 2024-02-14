package ms.hispam.budget.rules;

<<<<<<< HEAD
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ms.hispam.budget.dto.ParameterDTO;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.dto.RangeBuDTO;
import ms.hispam.budget.dto.RangeBuDetailDTO;
import ms.hispam.budget.repository.mysql.DaysVacationOfTimeRepository;
import ms.hispam.budget.service.DaysVacationOfTimeService;
=======
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
>>>>>>> old_change_peru_v3
import ms.hispam.budget.service.MexicoService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
<<<<<<< HEAD
import org.mockito.Mockito;
=======
>>>>>>> old_change_peru_v3
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {Mexico.class})
@ExtendWith(SpringExtension.class)
<<<<<<< HEAD
=======
@Slf4j(topic = "MexicoDiffblueTest")
>>>>>>> old_change_peru_v3
class MexicoDiffblueTest {
    @Autowired
    private Mexico mexico;

    @MockBean
    private MexicoService mexicoService;

    /**
<<<<<<< HEAD
     * Method under test: {@link Mexico#getAllDaysVacation(List, Integer)}
     */
    @Test
    void testGetAllDaysVacation() {
        // Arrange
        ArrayList<RangeBuDetailDTO> rangeBuDetailDTOList = new ArrayList<>();
        when(mexicoService.getAllDaysVacation(Mockito.<List<RangeBuDetailDTO>>any(), Mockito.<Integer>any()))
                .thenReturn(rangeBuDetailDTOList);

        // Act
        List<RangeBuDetailDTO> actualAllDaysVacation = mexico.getAllDaysVacation(new ArrayList<>(), 1);

        // Assert
        verify(mexicoService).getAllDaysVacation(Mockito.<List<RangeBuDetailDTO>>any(), Mockito.<Integer>any());
        assertTrue(actualAllDaysVacation.isEmpty());
        assertSame(rangeBuDetailDTOList, actualAllDaysVacation);
    }

    /**
     * Method under test: {@link Mexico#getAllDaysVacation(List, Integer)}
     */
    @Test
    void testGetAllDaysVacation2() {
        // Arrange
        ArrayList<RangeBuDetailDTO> rangeBuDetailDTOList = new ArrayList<>();
        when(mexicoService.getAllDaysVacation(Mockito.<List<RangeBuDetailDTO>>any(), Mockito.<Integer>any()))
                .thenReturn(rangeBuDetailDTOList);

        ArrayList<RangeBuDetailDTO> rangeBu = new ArrayList<>();
        RangeBuDetailDTO buildResult = RangeBuDetailDTO.builder().id(1).idPivot(1).range("Range").value(10.0d).build();
        rangeBu.add(buildResult);

        // Act
        List<RangeBuDetailDTO> actualAllDaysVacation = mexico.getAllDaysVacation(rangeBu, 1);

        // Assert
        verify(mexicoService).getAllDaysVacation(Mockito.<List<RangeBuDetailDTO>>any(), Mockito.<Integer>any());
        assertTrue(actualAllDaysVacation.isEmpty());
        assertSame(rangeBuDetailDTOList, actualAllDaysVacation);
    }

    /**
     * Method under test: {@link Mexico#getAllDaysVacation(List, Integer)}
     */
    @Test
    void testGetAllDaysVacation3() {
        // Arrange
        ArrayList<RangeBuDetailDTO> rangeBuDetailDTOList = new ArrayList<>();
        when(mexicoService.getAllDaysVacation(Mockito.<List<RangeBuDetailDTO>>any(), Mockito.<Integer>any()))
                .thenReturn(rangeBuDetailDTOList);

        ArrayList<RangeBuDetailDTO> rangeBu = new ArrayList<>();
        RangeBuDetailDTO buildResult = RangeBuDetailDTO.builder().id(1).idPivot(1).range("Range").value(10.0d).build();
        rangeBu.add(buildResult);
        RangeBuDetailDTO buildResult2 = RangeBuDetailDTO.builder().id(1).idPivot(1).range("Range").value(10.0d).build();
        rangeBu.add(buildResult2);

        // Act
        List<RangeBuDetailDTO> actualAllDaysVacation = mexico.getAllDaysVacation(rangeBu, 1);

        // Assert
        verify(mexicoService).getAllDaysVacation(Mockito.<List<RangeBuDetailDTO>>any(), Mockito.<Integer>any());
        assertTrue(actualAllDaysVacation.isEmpty());
        assertSame(rangeBuDetailDTOList, actualAllDaysVacation);
    }

    /**
     * Method under test: {@link Mexico#init(List, List)}
     */
    @Test
    void testInit() {
        //   Diffblue Cover was unable to write a Spring test,
        //   so wrote a non-Spring test instead.
        //   Reason: R002 Missing observers.
        //   Diffblue Cover was unable to create an assertion.
        //   Add getters for the following fields or make them package-private:
        //     Mexico.allDaysVacation
        //     Mexico.dateCache
        //     Mexico.incrementMap
        //     Mexico.mexicoService
        //     Mexico.salaryMap
        //     Mexico.salaryRevisionMap
        //     Mexico.sortedSalaryRevisions
        //     Mexico.vacationsDaysCache

        // Arrange
        DaysVacationOfTimeRepository daysVacationOfTimeRepository = mock(DaysVacationOfTimeRepository.class);
        Mexico mexico = new Mexico(new MexicoService(daysVacationOfTimeRepository, new DaysVacationOfTimeService()));
        ArrayList<ParametersDTO> salaryList = new ArrayList<>();

        // Act
        mexico.init(salaryList, new ArrayList<>());

        // Assert that nothing has changed
        assertNull(mexico.getAllDaysVacation(null, 1));
    }

    /**
     * Method under test: {@link Mexico#init(List, List)}
     */
    @Test
    void testInit2() {
        //   Diffblue Cover was unable to write a Spring test,
        //   so wrote a non-Spring test instead.
        //   Reason: R002 Missing observers.
        //   Diffblue Cover was unable to create an assertion.
        //   Add getters for the following fields or make them package-private:
        //     Mexico.allDaysVacation
        //     Mexico.dateCache
        //     Mexico.incrementMap
        //     Mexico.mexicoService
        //     Mexico.salaryMap
        //     Mexico.salaryRevisionMap
        //     Mexico.sortedSalaryRevisions
        //     Mexico.vacationsDaysCache

        // Arrange
        DaysVacationOfTimeRepository daysVacationOfTimeRepository = mock(DaysVacationOfTimeRepository.class);
        Mexico mexico = new Mexico(new MexicoService(daysVacationOfTimeRepository, new DaysVacationOfTimeService()));

        ArrayList<ParametersDTO> salaryList = new ArrayList<>();
        ParametersDTO.ParametersDTOBuilder orderResult = ParametersDTO.builder().order(1);
        ParameterDTO parameter = ParameterDTO.builder()
                .description("The characteristics of someone or something")
                .id(1)
                .name("Name")
                .typeValor(42)
                .build();
        ParametersDTO buildResult = orderResult.parameter(parameter)
                .period("Period")
                .periodRetroactive("Period Retroactive")
                .range("Range")
                .type(1)
                .value(10.0d)
                .build();
        salaryList.add(buildResult);

        ArrayList<ParametersDTO> incrementList = new ArrayList<>();
        ParametersDTO.ParametersDTOBuilder orderResult2 = ParametersDTO.builder().order(1);
        ParameterDTO parameter2 = ParameterDTO.builder()
                .description("The characteristics of someone or something")
                .id(1)
                .name("Name")
                .typeValor(42)
                .build();
        ParametersDTO buildResult2 = orderResult2.parameter(parameter2)
                .period("Period")
                .periodRetroactive("Period Retroactive")
                .range("Range")
                .type(1)
                .value(10.0d)
                .build();
        incrementList.add(buildResult2);

        // Act
        mexico.init(salaryList, incrementList);

        // Assert
        assertNull(mexico.getAllDaysVacation(null, 1));
    }

    /**
     * Method under test: {@link Mexico#init(List, List)}
     */
    @Test
    void testInit3() {
        //   Diffblue Cover was unable to write a Spring test,
        //   so wrote a non-Spring test instead.
        //   Reason: R002 Missing observers.
        //   Diffblue Cover was unable to create an assertion.
        //   Add getters for the following fields or make them package-private:
        //     Mexico.allDaysVacation
        //     Mexico.dateCache
        //     Mexico.incrementMap
        //     Mexico.mexicoService
        //     Mexico.salaryMap
        //     Mexico.salaryRevisionMap
        //     Mexico.sortedSalaryRevisions
        //     Mexico.vacationsDaysCache

        // Arrange
        DaysVacationOfTimeRepository daysVacationOfTimeRepository = mock(DaysVacationOfTimeRepository.class);
        Mexico mexico = new Mexico(new MexicoService(daysVacationOfTimeRepository, new DaysVacationOfTimeService()));

        ArrayList<ParametersDTO> salaryList = new ArrayList<>();
        ParametersDTO.ParametersDTOBuilder orderResult = ParametersDTO.builder().order(1);
        ParameterDTO parameter = ParameterDTO.builder()
                .description("The characteristics of someone or something")
                .id(1)
                .name("Name")
                .typeValor(42)
                .build();
        ParametersDTO buildResult = orderResult.parameter(parameter)
                .period("Period")
                .periodRetroactive("Period Retroactive")
                .range("Range")
                .type(1)
                .value(10.0d)
                .build();
        salaryList.add(buildResult);
        ParametersDTO.ParametersDTOBuilder orderResult2 = ParametersDTO.builder().order(1);
        ParameterDTO parameter2 = ParameterDTO.builder()
                .description("The characteristics of someone or something")
                .id(1)
                .name("Name")
                .typeValor(42)
                .build();
        ParametersDTO buildResult2 = orderResult2.parameter(parameter2)
                .period("Period")
                .periodRetroactive("Period Retroactive")
                .range("Range")
                .type(1)
                .value(10.0d)
                .build();
        salaryList.add(buildResult2);

        // Act
        mexico.init(salaryList, new ArrayList<>());

        // Assert
        assertNull(mexico.getAllDaysVacation(null, 1));
    }

    /**
     * Method under test: {@link Mexico#init(List, List)}
     */
    @Test
    void testInit4() {
        //   Diffblue Cover was unable to write a Spring test,
        //   so wrote a non-Spring test instead.
        //   Reason: R002 Missing observers.
        //   Diffblue Cover was unable to create an assertion.
        //   Add getters for the following fields or make them package-private:
        //     Mexico.allDaysVacation
        //     Mexico.dateCache
        //     Mexico.incrementMap
        //     Mexico.mexicoService
        //     Mexico.salaryMap
        //     Mexico.salaryRevisionMap
        //     Mexico.sortedSalaryRevisions
        //     Mexico.vacationsDaysCache

        // Arrange
        DaysVacationOfTimeRepository daysVacationOfTimeRepository = mock(DaysVacationOfTimeRepository.class);
        Mexico mexico = new Mexico(new MexicoService(daysVacationOfTimeRepository, new DaysVacationOfTimeService()));
        ArrayList<ParametersDTO> salaryList = new ArrayList<>();

        ArrayList<ParametersDTO> incrementList = new ArrayList<>();
        ParametersDTO.ParametersDTOBuilder orderResult = ParametersDTO.builder().order(1);
        ParameterDTO parameter = ParameterDTO.builder()
                .description("The characteristics of someone or something")
                .id(1)
                .name("Name")
                .typeValor(42)
                .build();
        ParametersDTO buildResult = orderResult.parameter(parameter)
                .period("Period")
                .periodRetroactive("Period Retroactive")
                .range("Range")
                .type(1)
                .value(10.0d)
                .build();
        incrementList.add(buildResult);
        ParametersDTO.ParametersDTOBuilder orderResult2 = ParametersDTO.builder().order(1);
        ParameterDTO parameter2 = ParameterDTO.builder()
                .description("The characteristics of someone or something")
                .id(1)
                .name("Name")
                .typeValor(42)
                .build();
        ParametersDTO buildResult2 = orderResult2.parameter(parameter2)
                .period("Period")
                .periodRetroactive("Period Retroactive")
                .range("Range")
                .type(1)
                .value(10.0d)
                .build();
        incrementList.add(buildResult2);

        // Act
        mexico.init(salaryList, incrementList);

        // Assert
        assertNull(mexico.getAllDaysVacation(null, 1));
    }

    /**
     * Method under test: {@link Mexico#updateSortedSalaryRevisions()}
     */
    @Test
    void testUpdateSortedSalaryRevisions() {
        //   Diffblue Cover was unable to write a Spring test,
        //   so wrote a non-Spring test instead.
        //   Reason: R002 Missing observers.
        //   Diffblue Cover was unable to create an assertion.
        //   Add getters for the following fields or make them package-private:
        //     Mexico.allDaysVacation
        //     Mexico.dateCache
        //     Mexico.incrementMap
        //     Mexico.mexicoService
        //     Mexico.salaryMap
        //     Mexico.salaryRevisionMap
        //     Mexico.sortedSalaryRevisions
        //     Mexico.vacationsDaysCache

        // Arrange
        DaysVacationOfTimeRepository daysVacationOfTimeRepository = mock(DaysVacationOfTimeRepository.class);
        Mexico mexico = new Mexico(new MexicoService(daysVacationOfTimeRepository, new DaysVacationOfTimeService()));

        // Act
        mexico.updateSortedSalaryRevisions();

        // Assert
        assertNull(mexico.getAllDaysVacation(null, 1));
    }

    /**
     * Method under test:
     * {@link Mexico#findClosestSalaryRevision(PaymentComponentDTO, String, Map, boolean, double)}
     */
    @Test
    void testFindClosestSalaryRevision() {
        // Arrange
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();

        // Act and Assert
        assertNull(mexico.findClosestSalaryRevision(salaryComponent, "Projection Month", new HashMap<>(), true, 10.0d));
    }

    /**
     * Method under test:
     * {@link Mexico#findClosestSalaryRevision(PaymentComponentDTO, String, Map, boolean, double)}
     */
    @Test
    void testFindClosestSalaryRevision2() {
        // Arrange
        PaymentComponentDTO salaryComponent = mock(PaymentComponentDTO.class);

        // Act and Assert
        assertNull(mexico.findClosestSalaryRevision(salaryComponent, "Projection Month", new HashMap<>(), true, 10.0d));
    }

    /**
     * Method under test:
     * {@link Mexico#salary(List, List, List, List, String, Integer, String)}
     */
    @Test
    @Disabled("TODO: Complete this test")
    void testSalary() {
        // TODO: Complete this test.
        //   Reason: R013 No inputs found that don't throw a trivial exception.
        //   Diffblue Cover tried to run the arrange/act section, but the method under
        //   test threw
        //   java.time.format.DateTimeParseException: Text 'Period' could not be parsed at index 0
        //       at java.base/java.time.format.DateTimeFormatter.parseResolved0(DateTimeFormatter.java:2106)
        //       at java.base/java.time.format.DateTimeFormatter.parse(DateTimeFormatter.java:2008)
        //       at java.base/java.time.YearMonth.parse(YearMonth.java:297)
        //       at ms.hispam.budget.util.Shared.generateMonthProjection(Shared.java:46)
        //       at ms.hispam.budget.rules.Mexico.createPaymentComponent(Mexico.java:103)
        //       at ms.hispam.budget.rules.Mexico.salary(Mexico.java:196)
        //   See https://diff.blue/R013 to resolve this issue.

        // Arrange
        ArrayList<PaymentComponentDTO> component = new ArrayList<>();
        ArrayList<ParametersDTO> salaryList = new ArrayList<>();
        ArrayList<ParametersDTO> incrementList = new ArrayList<>();

        // Act
        mexico.salary(component, salaryList, incrementList, new ArrayList<>(), "Period", 1, "Po Name");
    }

    /**
     * Method under test: {@link Mexico#provAguinaldo(List, String, Integer)}
     */
    @Test
    void testProvAguinaldo() {
=======
     * Method under test: {@link Mexico#seguroDental(List, List, String, Integer)}
     */
    @Test
    void testSeguroDental() {
        // Arrange
        ArrayList<PaymentComponentDTO> component = new ArrayList<>();

        // Act and Assert
        assertThrows(RuntimeException.class, () -> mexico.seguroDental(component, new ArrayList<>(), "Period", 1));
    }

    /**
     * Method under test: {@link Mexico#seguroDental(List, List, String, Integer)}
     */
    @Test
    void testSeguroDental2() {
>>>>>>> old_change_peru_v3
        // Arrange
        ArrayList<PaymentComponentDTO> component = new ArrayList<>();
        PaymentComponentDTO.PaymentComponentDTOBuilder builderResult = PaymentComponentDTO.builder();
        PaymentComponentDTO.PaymentComponentDTOBuilder paymentComponentResult = builderResult.amount(new BigDecimal("2.3"))
                .amountString("10")
                .name("Name")
<<<<<<< HEAD
                .paymentComponent("SALARY");
        PaymentComponentDTO buildResult = paymentComponentResult.projections(new ArrayList<>())
                .salaryType("Salary Type")
                .show(true)
                .type(1)
                .build();
        component.add(buildResult);

        // Act
        mexico.provAguinaldo(component, "Period", 1);

        // Assert
        assertEquals(2, component.size());
    }

    /**
     * Method under test: {@link Mexico#provAguinaldo(List, String, Integer)}
     */
    @Test
    void testProvAguinaldo2() {
        // Arrange
        PaymentComponentDTO.PaymentComponentDTOBuilder paymentComponentDTOBuilder = mock(
                PaymentComponentDTO.PaymentComponentDTOBuilder.class);
        when(paymentComponentDTOBuilder.amount(Mockito.<BigDecimal>any())).thenReturn(PaymentComponentDTO.builder());
        PaymentComponentDTO.PaymentComponentDTOBuilder paymentComponentResult = paymentComponentDTOBuilder
                .amount(new BigDecimal("2.3"))
                .amountString("10")
                .name("Name")
=======
>>>>>>> old_change_peru_v3
                .paymentComponent("Payment Component");
        PaymentComponentDTO buildResult = paymentComponentResult.projections(new ArrayList<>())
                .salaryType("Salary Type")
                .show(true)
                .type(1)
                .build();
<<<<<<< HEAD

        ArrayList<PaymentComponentDTO> component = new ArrayList<>();
        component.add(buildResult);

        // Act
        mexico.provAguinaldo(component, "Period", 1);

        // Assert that nothing has changed
        verify(paymentComponentDTOBuilder).amount(Mockito.<BigDecimal>any());
=======
        component.add(buildResult);

        // Act and Assert
        assertThrows(RuntimeException.class, () -> mexico.seguroDental(component, new ArrayList<>(), "Period", 1));
    }

    /**
     * Method under test: {@link Mexico#seguroDental(List, List, String, Integer)}
     * Scenario: Calcular la participación de los trabajadores para un empleado
     * Given que estoy en la página de cálculo de la nómina después de ingresar los
     * detalles del empleado When selecciono calcular la participación de los
     * trabajadores Then la participación de los trabajadores debería calcularse
     * correctamente And debería poder continuar con el proceso de cálculo de la
     * nómina
     */
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
        mexico.seguroDental(component, parameters, period, range);

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

    /**
     * Method under test: {@link Mexico#seguroVida(List, List, String, Integer)}
     * (List, List, String, Integer)}
     */

    /*
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
     */
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
        mexico.participacionTrabajadores(component, new ArrayList<>(), parameters, period, range);

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
>>>>>>> old_change_peru_v3
    }

    /**
     * Method under test:
<<<<<<< HEAD
     * {@link Mexico#provVacacionesRefactor(List, List, String, String, Integer, LocalDate, LocalDate, RangeBuDTO, Integer)}
     */
    @Test
    void testProvVacacionesRefactor() {
        // Arrange
        ArrayList<PaymentComponentDTO> component = new ArrayList<>();
        ArrayList<ParametersDTO> parameters = new ArrayList<>();
        LocalDate dateContract = LocalDate.of(1970, 1, 1);
        LocalDate dateBirth = LocalDate.of(1970, 1, 1);
        RangeBuDTO rangeBuByBU = mock(RangeBuDTO.class);
        when(rangeBuByBU.getRangeBuDetails()).thenReturn(new ArrayList<>());

        // Act
        mexico.provVacacionesRefactor(component, parameters, "Class Employee", "Period", 1, dateContract, dateBirth,
                rangeBuByBU, 1);

        // Assert
        verify(rangeBuByBU).getRangeBuDetails();
    }
}
=======
     * {@link Mexico#participacionTrabajadores(List, List, List, String, Integer)}
     */
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
        mexico.participacionTrabajadores(component, employeeParticipationList, new ArrayList<>(), "Period", 1);
    }

}

>>>>>>> old_change_peru_v3
