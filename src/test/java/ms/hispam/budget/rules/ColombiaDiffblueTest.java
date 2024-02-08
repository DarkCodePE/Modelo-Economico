package ms.hispam.budget.rules;

import java.util.ArrayList;
import java.util.List;

import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class ColombiaDiffblueTest {
    /**
     * Method under test:
     * {@link Colombia#salary(List, List, String, String, Integer, List, List, List, List)}
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
        //       at ms.hispam.budget.rules.Colombia.createSalaryComponent(Colombia.java:58)
        //       at ms.hispam.budget.rules.Colombia.salary(Colombia.java:247)
        //   See https://diff.blue/R013 to resolve this issue.

        // Arrange
        Colombia colombia = new Colombia();
        ArrayList<PaymentComponentDTO> component = new ArrayList<>();
        ArrayList<ParametersDTO> parameters = new ArrayList<>();
        ArrayList<ParametersDTO> legalSalaryMinList = new ArrayList<>();
        ArrayList<ParametersDTO> revisionSalaryMinList = new ArrayList<>();
        ArrayList<ParametersDTO> revisionSalaryMinEttList = new ArrayList<>();

        // Act
        colombia.salary(component, parameters, "Class Employee", "Period", 1, legalSalaryMinList, revisionSalaryMinList,
                revisionSalaryMinEttList, new ArrayList<>());
    }

    /**
     * Method under test:
     * {@link Colombia#temporalSalary(List, List, String, String, Integer, List, List, List, List)}
     */
    @Test
    @Disabled("TODO: Complete this test")
    void testTemporalSalary() {
        // TODO: Complete this test.
        //   Reason: R013 No inputs found that don't throw a trivial exception.
        //   Diffblue Cover tried to run the arrange/act section, but the method under
        //   test threw
        //   java.time.format.DateTimeParseException: Text 'Period' could not be parsed at index 0
        //       at java.base/java.time.format.DateTimeFormatter.parseResolved0(DateTimeFormatter.java:2106)
        //       at java.base/java.time.format.DateTimeFormatter.parse(DateTimeFormatter.java:2008)
        //       at java.base/java.time.YearMonth.parse(YearMonth.java:297)
        //       at ms.hispam.budget.util.Shared.generateMonthProjection(Shared.java:46)
        //       at ms.hispam.budget.rules.Colombia.createSalaryComponent(Colombia.java:58)
        //       at ms.hispam.budget.rules.Colombia.temporalSalary(Colombia.java:311)
        //   See https://diff.blue/R013 to resolve this issue.

        // Arrange
        Colombia colombia = new Colombia();
        ArrayList<PaymentComponentDTO> component = new ArrayList<>();
        ArrayList<ParametersDTO> parameters = new ArrayList<>();
        ArrayList<ParametersDTO> legalSalaryMinList = new ArrayList<>();
        ArrayList<ParametersDTO> revisionSalaryMinList = new ArrayList<>();
        ArrayList<ParametersDTO> revisionSalaryMinEttList = new ArrayList<>();

        // Act
        colombia.temporalSalary(component, parameters, "Class Employee", "Period", 1, legalSalaryMinList,
                revisionSalaryMinList, revisionSalaryMinEttList, new ArrayList<>());
    }
}
