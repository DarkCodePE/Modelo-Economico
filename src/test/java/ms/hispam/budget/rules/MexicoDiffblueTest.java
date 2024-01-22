package ms.hispam.budget.rules;

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
import ms.hispam.budget.service.MexicoService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {Mexico.class})
@ExtendWith(SpringExtension.class)
class MexicoDiffblueTest {
    @Autowired
    private Mexico mexico;

    @MockBean
    private MexicoService mexicoService;

    /**
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
        // Arrange
        ArrayList<PaymentComponentDTO> component = new ArrayList<>();
        PaymentComponentDTO.PaymentComponentDTOBuilder builderResult = PaymentComponentDTO.builder();
        PaymentComponentDTO.PaymentComponentDTOBuilder paymentComponentResult = builderResult.amount(new BigDecimal("2.3"))
                .amountString("10")
                .name("Name")
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
                .paymentComponent("Payment Component");
        PaymentComponentDTO buildResult = paymentComponentResult.projections(new ArrayList<>())
                .salaryType("Salary Type")
                .show(true)
                .type(1)
                .build();

        ArrayList<PaymentComponentDTO> component = new ArrayList<>();
        component.add(buildResult);

        // Act
        mexico.provAguinaldo(component, "Period", 1);

        // Assert that nothing has changed
        verify(paymentComponentDTOBuilder).amount(Mockito.<BigDecimal>any());
    }

    /**
     * Method under test:
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
