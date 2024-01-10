package ms.hispam.budget.rules.operations.salary;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.rules.countries.Country;
import ms.hispam.budget.rules.operations.Mediator;
import ms.hispam.budget.rules.operations.Operation;
import ms.hispam.budget.util.Shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
@Slf4j(topic = "Peru")
public class Peru implements Country, Mediator {
    private final List<Operation> operations;
    static final String TYPEMONTH="yyyyMM";
    private static final String THEORETICAL_SALARY = "THEORETICAL-SALARY";
    private static final String VACATION_ENJOYMENT = "VACATION-ENJOYMENT";
    private static final String BASE_SALARY = "BASE-SALARY";
    private static final String VACATION_PROVISION = "VACATION-PROVISION";
    private static final String GRATIFICATIONS = "GRATIFICATIONS";
    private static final String FOOD_BENEFITS = "FOOD-BENEFITS";
    private static final String LIFE_INSURANCE = "LIFE-INSURANCE";
    private static final String MOVING = "MOVING";
    private static final String HOUSING = "HOUSING";
    private static final String EXPATRIATES= "EXPATRIATES";
    private static final String AFP = "AFP";
    private static final String FOOD_TEMPORAL_LIST = "V. Alimentación";
    private static final String LIFE_INSURANCE_LIST = "SV Ley";
    private static final String PC960400 = "PC960400";
    private static final String PC960401 = "PC960401";

    public Peru(List<Operation> operations) {
        this.operations = operations;
    }

    @Override
    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<RangeBuDTO> temporalParameters, LocalDate birthDate,  Map<String, List<Double>> vacationSeasonalityValues) {
        for (Operation operation : operations) {
            operation.execute(this, component, parameters, classEmployee, period, range, temporalParameters, birthDate, vacationSeasonalityValues);
        }
    }
    @Override
    public void executeOperation(Operation operation, List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<RangeBuDTO> temporalParameters, LocalDate birthDate, Map<String, List<Double>> vacationSeasonalityValues) {
        if (operation instanceof SalaryOperationPeru) {
            processSalaryOperation(component, parameters, period, range);
        } else if (operation instanceof RevisionSalaryOperationPeru) {
            List<ParametersDTO> salaryParametersList = getListParametersById(parameters, classEmployee.equals("EMP") ? 34 : 35);
            List<ParametersDTO> revSalParametersList = getListParametersById(parameters, 37);
            processRevisionSalaryOperation(component, salaryParametersList, revSalParametersList);
        }else if (operation instanceof VacationEnjoymentOperation) {
             processVacationEnjoymentOperation(component, parameters, vacationSeasonalityValues);
        }else if (operation instanceof BaseSalaryOperation) {
            processBaseSalaryOperation(component, parameters);
        }else if (operation instanceof VacationProvisionOperation) {
            processVacationProvisionOperation(component, parameters);
        }else if(operation instanceof GratificationOperation) {
            processGratificationsOperation(component);
        }else if(operation instanceof FoodBenefitsOperation) {
            processFoodBenefitsOperation(component, temporalParameters, classEmployee, period, range);
        }else if(operation instanceof CTSOperation) {
            processCTSOperation(component);
        }else if(operation instanceof LifeInsuranceOperation) {
            processLifeInsuranceOperation(component, temporalParameters,parameters, birthDate);
        }else if(operation instanceof MovingOperation){
            processMovingOperation(component, period, range);
        }else if(operation instanceof HousingOperation) {
            processHousingOperation(component, period, range);
        }else if(operation instanceof ExpatriateseOperation) {
            processExpatriateseOperation(component, period, range);
        }else if(operation instanceof AFPOperation) {
            processAFPOperation(component, parameters, period, range);
        }
    }

    private void processAFPOperation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO AFPComponent = componentMap.get(AFP);
        log.info("processAFPOperation -> {}", AFPComponent);
        if (AFPComponent != null) {
            PaymentComponentDTO AFPComponentNew = new PaymentComponentDTO();
            AFPComponentNew.setPaymentComponent(AFP);
            AFPComponentNew.setAmount(BigDecimal.valueOf(AFPComponent.getAmount().doubleValue()));
            List<MonthProjection> AFPProjections = new ArrayList<>();
            for (MonthProjection AFPProjection : AFPComponent.getProjections()) {
                MonthProjection AFPProjectionNew = new MonthProjection();
                AFPProjectionNew.setMonth(AFPProjection.getMonth());
                AFPProjectionNew.setAmount(BigDecimal.valueOf(AFPProjection.getAmount().doubleValue()));
                AFPProjections.add(AFPProjectionNew);
            }
            AFPComponentNew.setProjections(AFPProjections);
            component.add(AFPComponentNew);
        }else {
            PaymentComponentDTO AFPComponentNew = new PaymentComponentDTO();
            AFPComponentNew.setPaymentComponent(AFP);
            AFPComponentNew.setAmount(BigDecimal.valueOf(0.0));
            AFPComponentNew.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(AFPComponentNew);
        }
    }

    private void processExpatriateseOperation(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO expatriateseComponent = componentMap.get(EXPATRIATES);
        log.info("processExpatriateseOperation -> {}", expatriateseComponent);
        if (expatriateseComponent != null) {
            PaymentComponentDTO expatriateseComponentNew = new PaymentComponentDTO();
            expatriateseComponentNew.setPaymentComponent(EXPATRIATES);
            expatriateseComponentNew.setAmount(BigDecimal.valueOf(expatriateseComponent.getAmount().doubleValue()));
            List<MonthProjection> expatriateseProjections = new ArrayList<>();
            for (MonthProjection expatriateseProjection : expatriateseComponent.getProjections()) {
                MonthProjection expatriateseProjectionNew = new MonthProjection();
                expatriateseProjectionNew.setMonth(expatriateseProjection.getMonth());
                expatriateseProjectionNew.setAmount(BigDecimal.valueOf(expatriateseProjection.getAmount().doubleValue()));
                expatriateseProjections.add(expatriateseProjectionNew);
            }
            expatriateseComponentNew.setProjections(expatriateseProjections);
            component.add(expatriateseComponentNew);
        }else {
            PaymentComponentDTO expatriateseComponentNew = new PaymentComponentDTO();
            expatriateseComponentNew.setPaymentComponent(EXPATRIATES);
            expatriateseComponentNew.setAmount(BigDecimal.valueOf(0.0));
            expatriateseComponentNew.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(expatriateseComponentNew);
        }
    }

    private void processHousingOperation(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO housingComponent = componentMap.get(HOUSING);
        log.info("processHousingOperation -> {}", housingComponent);
        if (housingComponent != null) {
            PaymentComponentDTO housingComponentNew = new PaymentComponentDTO();
            housingComponentNew.setPaymentComponent(HOUSING);
            housingComponentNew.setAmount(BigDecimal.valueOf(housingComponent.getAmount().doubleValue()));
            List<MonthProjection> housingProjections = new ArrayList<>();
            for (MonthProjection housingProjection : housingComponent.getProjections()) {
                MonthProjection housingProjectionNew = new MonthProjection();
                housingProjectionNew.setMonth(housingProjection.getMonth());
                housingProjectionNew.setAmount(BigDecimal.valueOf(housingProjection.getAmount().doubleValue()));
                housingProjections.add(housingProjectionNew);
            }
            housingComponentNew.setProjections(housingProjections);
            component.add(housingComponentNew);
        }else {
            PaymentComponentDTO housingComponentNew = new PaymentComponentDTO();
            housingComponentNew.setPaymentComponent(HOUSING);
            housingComponentNew.setAmount(BigDecimal.valueOf(0.0));
            housingComponentNew.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(housingComponentNew);
        }
    }

    private void processMovingOperation(List<PaymentComponentDTO> component, String period, Integer range) {
        // Obtener el PaymentComponentDTO Moving
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO movingComponent = componentMap.get(MOVING);
        log.info("processMovingOperation -> {}", movingComponent);
        if (movingComponent != null) {
            // Crear el PaymentComponentDTO para Moving
            PaymentComponentDTO movingComponentNew = new PaymentComponentDTO();
            movingComponentNew.setPaymentComponent(MOVING);
            movingComponentNew.setAmount(BigDecimal.valueOf(movingComponent.getAmount().doubleValue()));
            List<MonthProjection> movingProjections = new ArrayList<>();
            // Iterar sobre las proyecciones de Moving
            for (MonthProjection movingProjection : movingComponent.getProjections()) {
                // Crear una nueva proyección para Moving
                MonthProjection movingProjectionNew = new MonthProjection();
                movingProjectionNew.setMonth(movingProjection.getMonth());
                movingProjectionNew.setAmount(BigDecimal.valueOf(movingProjection.getAmount().doubleValue()));
                // Agregar la proyección a Moving
                movingProjections.add(movingProjectionNew);
            }
            movingComponentNew.setProjections(movingProjections);
            // Agregar Moving a la lista de componentes
            component.add(movingComponentNew);
        }else {
            PaymentComponentDTO movingComponentNew = new PaymentComponentDTO();
            movingComponentNew.setPaymentComponent(MOVING);
            movingComponentNew.setAmount(BigDecimal.valueOf(0.0));
            movingComponentNew.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(movingComponentNew);
        }

    }

    private void processLifeInsuranceOperation(List<PaymentComponentDTO> component, List<RangeBuDTO> temporalParameters,List<ParametersDTO> parameters, LocalDate birthDate) {
        // Obtener el PaymentComponentDTO para THEORETICAL_SALARY
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        // Obtener los PaymentComponentDTO para THEORETICAL_SALARY y GRATIFICATIONS
        PaymentComponentDTO theoreticalSalaryComponent = componentMap.get(THEORETICAL_SALARY);
        // Obtener el parámetro SV grupo
        ParametersDTO svGroupParameter = getParametersById(parameters, 41);
        double svGroupParameterDouble = svGroupParameter != null ? svGroupParameter.getValue() : 0.09;
        if (theoreticalSalaryComponent != null) {
            PaymentComponentDTO lifeInsuranceComponent = new PaymentComponentDTO();
            lifeInsuranceComponent.setPaymentComponent(LIFE_INSURANCE);
            int ageInit = calculateAge(birthDate, theoreticalSalaryComponent.getProjections().get(0).getMonth());
            Optional<RangeBuDetailDTO> matchingDetailInit = findMatchingDetail(temporalParameters, ageInit);
            double lifeInsuranceInit = matchingDetailInit.isPresent() ? matchingDetailInit.get().getValue() : 0.0;
            lifeInsuranceComponent.setAmount(BigDecimal.valueOf(theoreticalSalaryComponent.getAmount().doubleValue() * ((svGroupParameterDouble + lifeInsuranceInit) / 100 )));
            List<MonthProjection> lifeInsuranceProjections = new ArrayList<>();
            for (MonthProjection salaryProjection : theoreticalSalaryComponent.getProjections()) {
                int age = calculateAge(birthDate, salaryProjection.getMonth());
                Optional<RangeBuDetailDTO> matchingDetail = findMatchingDetail(temporalParameters, age);
                double lifeInsurance = matchingDetail.isPresent() ? matchingDetail.get().getValue() : 0.0;
                double lifeInsuranceValue = calculateLifeInsuranceValue(salaryProjection, svGroupParameterDouble, lifeInsurance);
                MonthProjection lifeInsuranceProjection = createLifeInsuranceProjection(salaryProjection, lifeInsuranceValue);
                lifeInsuranceProjections.add(lifeInsuranceProjection);
            }
            //log.info("{}", lifeInsuranceProjections);
            lifeInsuranceComponent.setProjections(lifeInsuranceProjections);
            component.add(lifeInsuranceComponent);
        }
    }
    private int calculateAge(LocalDate birthDate, String projectionMonth) {
        return Period.between(birthDate, LocalDate.parse(projectionMonth + "01", DateTimeFormatter.ofPattern("yyyyMMdd"))).getYears();
    }

    private Optional<RangeBuDetailDTO> findMatchingDetail(List<RangeBuDTO> temporalParameters, int age) {
        return temporalParameters.stream()
                .filter(rangeBuDTO -> rangeBuDTO.getName().equalsIgnoreCase(LIFE_INSURANCE_LIST))
                .flatMap(rangeBuDTO -> rangeBuDTO.getRangeBuDetails().stream())
                .filter(detail -> {
                    String[] rangeLimits = detail.getRange().trim().split("a");
                    int lowerLimit = Integer.parseInt(rangeLimits[0].trim());
                    int upperLimit = Integer.parseInt(rangeLimits[1].trim());
                    return age >= lowerLimit && age <= upperLimit;
                })
                .findFirst();
    }

    private double calculateLifeInsuranceValue(MonthProjection salaryProjection, double svGroupParameter, double matchingDetail) {
        return salaryProjection.getAmount().doubleValue() * ((svGroupParameter + matchingDetail) / 100);
    }

    private MonthProjection createLifeInsuranceProjection(MonthProjection salaryProjection, double lifeInsuranceValue) {
        MonthProjection lifeInsuranceProjection = new MonthProjection();
        lifeInsuranceProjection.setMonth(salaryProjection.getMonth());
        lifeInsuranceProjection.setAmount(BigDecimal.valueOf(lifeInsuranceValue));
        return lifeInsuranceProjection;
    }
    private void processCTSOperation(List<PaymentComponentDTO> component) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        // Obtener los PaymentComponentDTO para THEORETICAL_SALARY y GRATIFICATIONS
        PaymentComponentDTO theoreticalSalaryComponent = componentMap.get(THEORETICAL_SALARY);
        PaymentComponentDTO gratificationsComponent = componentMap.get(GRATIFICATIONS);
        if (theoreticalSalaryComponent != null && gratificationsComponent != null) {
            // Crear el PaymentComponentDTO para CTS
            PaymentComponentDTO ctsComponent = new PaymentComponentDTO();
            ctsComponent.setPaymentComponent("CTS");
            // Calcular el valor de CTS a partir de THEORETICAL_SALARY y GRATIFICATIONS
            double theoreticalSalary = theoreticalSalaryComponent.getAmount().doubleValue();
            double gratifications = gratificationsComponent.getAmount().doubleValue();
            double ctsValue = theoreticalSalary / 12 + gratifications / 2 / 6;
            ctsComponent.setAmount(BigDecimal.valueOf(ctsValue));
            // Crear una lista para las proyecciones de CTS
            List<MonthProjection> ctsProjections = new ArrayList<>();
            // Iterar sobre las proyecciones de THEORETICAL_SALARY y GRATIFICATIONS
            for (int i = 0; i < theoreticalSalaryComponent.getProjections().size(); i++) {
                MonthProjection theoreticalSalaryProjection = theoreticalSalaryComponent.getProjections().get(i);
                MonthProjection gratificationsProjection = gratificationsComponent.getProjections().get(i);
                // Calcular el valor de CTS para el mes
                double monthlyTheoreticalSalary = theoreticalSalaryProjection.getAmount().doubleValue();
                double monthlyGratifications = gratificationsProjection.getAmount().doubleValue();
                double monthlyCtsValue = monthlyTheoreticalSalary / 12.0 + (monthlyGratifications / 2.0) / 6.0;
                // Crear una nueva proyección para CTS
                MonthProjection ctsProjection = new MonthProjection();
                ctsProjection.setMonth(theoreticalSalaryProjection.getMonth());
                ctsProjection.setAmount(BigDecimal.valueOf(monthlyCtsValue));
                // Agregar la proyección a CTS
                ctsProjections.add(ctsProjection);
            }
            // Establecer las proyecciones de CTS en ctsComponent
            ctsComponent.setProjections(ctsProjections);
            // Agregar CTS a la lista de componentes
            component.add(ctsComponent);
        }
    }

    private void processFoodBenefitsOperation(List<PaymentComponentDTO> component, List<RangeBuDTO> temporalParameters, String classEmployee, String period, Integer range) {
        // Calcular el valor de los beneficios alimentarios
        double foodBenefitsValue = calculateFoodBenefits(classEmployee, temporalParameters);
        // Crear un nuevo PaymentComponentDTO para los beneficios alimentarios
        PaymentComponentDTO foodBenefitsComponent = new PaymentComponentDTO();
        foodBenefitsComponent.setPaymentComponent(FOOD_BENEFITS);
        foodBenefitsComponent.setAmount(BigDecimal.valueOf(foodBenefitsValue));
        foodBenefitsComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(foodBenefitsValue)));
        // Agregar el nuevo PaymentComponentDTO a la lista de componentes
        component.add(foodBenefitsComponent);
    }
    private double calculateFoodBenefits(String classEmployee, List<RangeBuDTO> temporalParameters) {
        //log.info("{}", temporalParameters);
        //log.info("{}", classEmployee);
        Optional<RangeBuDetailDTO> matchingDetail = temporalParameters.stream()
                .filter(rangeBuDTO -> rangeBuDTO.getName().equalsIgnoreCase(FOOD_TEMPORAL_LIST))
                .flatMap(rangeBuDTO -> rangeBuDTO.getRangeBuDetails().stream())
                .filter(detail -> detail.getRange().contains(classEmployee))
                .findFirst();
        //throw new IllegalArgumentException("No se encontró un rango que coincida con el valor de entrada");
        return matchingDetail.map(rangeBuDetailDTO -> rangeBuDetailDTO.getValue() / 12.0).orElse(0.0);
    }
    private void processBaseSalaryOperation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
       // Obtener los PaymentComponentDTO para THEORETICAL_SALARY y VACATION_ENJOYMENT
        PaymentComponentDTO theoreticalSalaryComponent = componentMap.get(THEORETICAL_SALARY);
        PaymentComponentDTO vacationEnjoymentComponent = componentMap.get(VACATION_ENJOYMENT);
        //log.info("{}", theoreticalSalaryComponent);
        //log.info("{}", vacationEnjoymentComponent);
        if (theoreticalSalaryComponent != null && vacationEnjoymentComponent != null) {
            // Crear el PaymentComponentDTO para baseSalary
            PaymentComponentDTO baseSalaryComponent = new PaymentComponentDTO();
            baseSalaryComponent.setPaymentComponent(BASE_SALARY);
            // Calcular el valor de baseSalary a partir de THEORETICAL_SALARY y VACATION_ENJOYMENT
            double theoreticalSalaryComp = theoreticalSalaryComponent.getAmount().doubleValue();
            double vacationEnjoymentComp = vacationEnjoymentComponent.getAmount().doubleValue();
            baseSalaryComponent.setAmount(BigDecimal.valueOf(theoreticalSalaryComp - vacationEnjoymentComp));
            // Iterar sobre las proyecciones de THEORETICAL_SALARY y VACATION_ENJOYMENT
            List<MonthProjection> projections = new ArrayList<>();
            for (int i = 0; i < theoreticalSalaryComponent.getProjections().size(); i++) {
                MonthProjection theoreticalSalaryProjection = theoreticalSalaryComponent.getProjections().get(i);
                MonthProjection vacationEnjoymentProjection = vacationEnjoymentComponent.getProjections().get(i);
                // Calcular el valor de BASE_SALARY para el mes
                double theoreticalSalary = theoreticalSalaryProjection.getAmount().doubleValue();
                double vacationEnjoyment = vacationEnjoymentProjection.getAmount().doubleValue();
                double baseSalary = theoreticalSalary - vacationEnjoyment;
                // Crear una nueva proyección para BASE_SALARY
                MonthProjection baseSalaryProjection = new MonthProjection();
                baseSalaryProjection.setMonth(theoreticalSalaryProjection.getMonth());
                baseSalaryProjection.setAmount(BigDecimal.valueOf(baseSalary));
                // Agregar la proyección a BASE_SALARY
                projections.add(baseSalaryProjection);
            }
            baseSalaryComponent.setProjections(projections);
            // Agregar baseSalaryComponent a la lista de componentes
            component.add(baseSalaryComponent);
        }
    }
    private void processGratificationsOperation(List<PaymentComponentDTO> component) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        // Obtener el PaymentComponentDTO para THEORETICAL_SALARY
        PaymentComponentDTO theoreticalSalaryComponent = componentMap.get(THEORETICAL_SALARY);
        if (theoreticalSalaryComponent != null) {
            // Crear el PaymentComponentDTO para GRATIFICATIONS
            PaymentComponentDTO gratificationsComponent = new PaymentComponentDTO();
            gratificationsComponent.setPaymentComponent(GRATIFICATIONS);
            gratificationsComponent.setAmount(BigDecimal.valueOf((theoreticalSalaryComponent.getAmount().doubleValue() * 2)/12));
            // Crear una lista para las proyecciones de GRATIFICATIONS
            List<MonthProjection> gratificationsProjections = new ArrayList<>();
            // Iterar sobre las proyecciones de THEORETICAL_SALARY
            for (MonthProjection theoreticalSalaryProjection : theoreticalSalaryComponent.getProjections()) {
                double theoreticalSalary = theoreticalSalaryProjection.getAmount().doubleValue();
                // Calcular GRATIFICATIONS usando la fórmula (AA15*2/12)
                double gratificationsValue = (theoreticalSalary * 2) / 12;
                // Crear una nueva proyección para GRATIFICATIONS
                MonthProjection gratificationsProjection = new MonthProjection();
                gratificationsProjection.setMonth(theoreticalSalaryProjection.getMonth());
                gratificationsProjection.setAmount(BigDecimal.valueOf(gratificationsValue));
                // Agregar la proyección a la lista de proyecciones de GRATIFICATIONS
                gratificationsProjections.add(gratificationsProjection);
            }
            // Establecer las proyecciones de GRATIFICATIONS en gratificationsComponent
            gratificationsComponent.setProjections(gratificationsProjections);
            // Agregar GRATIFICATIONS a la lista de componentes
            component.add(gratificationsComponent);
        }
    }
    private void processVacationEnjoymentOperation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, Map<String, List<Double>> vacationSeasonalityValues) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get(THEORETICAL_SALARY);
        PaymentComponentDTO enjoymentComponent = componentMap.get("goce");
        if (salaryComponent != null){
            ParametersDTO vacationDays = getParametersById(parameters, 39);
            //List<ParametersDTO> vacationSeasonalityList = getListParametersById(parameters, 40);
            double vacationDaysValue = 30;
            if (vacationDays != null) vacationDaysValue = vacationDays.getValue();
            double enjoymentValue = enjoymentComponent != null ? enjoymentComponent.getAmount().doubleValue() : 0.7;
            //Map<String, List<Double>> vacationSeasonalityValues = calculateVacationSeasonality(vacationSeasonalityList);
            //log.info("{}", vacationSeasonalityValues);
            PaymentComponentDTO vacationEnjoymentComponent = new PaymentComponentDTO();
            vacationEnjoymentComponent.setPaymentComponent(VACATION_ENJOYMENT);
            vacationEnjoymentComponent.setAmount(BigDecimal.valueOf((salaryComponent.getAmount().doubleValue()/30)* vacationDaysValue* enjoymentValue*8.33)) ;
            List<MonthProjection> projections = new ArrayList<>();
            //log.info("{}", salaryComponent.getProjections());
            int i = 0;
            for (MonthProjection projection : salaryComponent.getProjections()) {
                //log.info("projection.getMonth() -> {}", projection.getMonth());
                if (projection.getMonth() != null) {
                    double amount = projection.getAmount().doubleValue();
                    String projectionYearMonth = projection.getMonth().substring(0, 6); // Get the year and month of the projection
                    double vacationSeasonalityValue = 8.33;
                    if (vacationSeasonalityValues != null) {
                        //log.info("{}", projection.getMonth());
                        String year = projection.getMonth().substring(0, 4); // Get the year of the projectio
                        //log.info("{}", year);
                        List<Double> vacationSeasonalityForYear = vacationSeasonalityValues.get(year); // Get the vacation seasonality for the year of the projection
                        //log.info("{}", vacationSeasonalityForYear);
                        int index = Integer.parseInt(projectionYearMonth.substring(4)) - 1;
                        vacationSeasonalityValue = vacationSeasonalityForYear.get(index) / 100;
                        log.info("contardor: -> {}", i++);
                    }
                    double value = (amount / 30) * vacationDaysValue * enjoymentValue * vacationSeasonalityValue;
                    MonthProjection vacationEnjoymentProjection = new MonthProjection();
                    vacationEnjoymentProjection.setMonth(projection.getMonth());
                    vacationEnjoymentProjection.setAmount(BigDecimal.valueOf(value));
                    projections.add(vacationEnjoymentProjection);
                }
            }
            vacationEnjoymentComponent.setProjections(projections);
            component.add(vacationEnjoymentComponent);
        }
    }
    private void processVacationProvisionOperation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        // Obtener el PaymentComponentDTO para THEORETICAL_SALARY
        PaymentComponentDTO theoreticalSalaryComponent = componentMap.get(THEORETICAL_SALARY);
        // Obtener el parámetro Días vacaciones
        ParametersDTO vacationDaysParameter = getParametersById(parameters, 39);
        if (theoreticalSalaryComponent != null) {
            double vacationDays = vacationDaysParameter != null ? vacationDaysParameter.getValue() : 30;
            // Crear el PaymentComponentDTO para VACATION-PROVISION
            PaymentComponentDTO vacationProvisionComponent = new PaymentComponentDTO();
            vacationProvisionComponent.setPaymentComponent(VACATION_PROVISION);
            vacationProvisionComponent.setAmount(BigDecimal.valueOf((theoreticalSalaryComponent.getAmount().doubleValue()/30)*(vacationDays/12)));
            vacationProvisionComponent.setProjections(new ArrayList<>());
            // Iterar sobre las proyecciones de THEORETICAL_SALARY
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection theoreticalSalaryProjection : theoreticalSalaryComponent.getProjections()) {
                double theoreticalSalary = theoreticalSalaryProjection.getAmount().doubleValue();
                // Calcular VACATION-PROVISION usando la fórmula (AA15/30)*$Z$7/12
                double vacationProvisionValue = (theoreticalSalary / 30) * (vacationDays / 12);
                // Crear una nueva proyección para VACATION-PROVISION
                MonthProjection vacationProvisionProjection = new MonthProjection();
                vacationProvisionProjection.setMonth(theoreticalSalaryProjection.getMonth());
                vacationProvisionProjection.setAmount(BigDecimal.valueOf(vacationProvisionValue));
                // Agregar la proyección a VACATION-PROVISION
                projections.add(vacationProvisionProjection);
            }
            vacationProvisionComponent.setProjections(projections);
            // Agregar VACATION-PROVISION a la lista de componentes
            component.add(vacationProvisionComponent);
        }
    }
    private void processSalaryOperation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc960400Component = componentMap.get(PC960400); // componente 960400
        PaymentComponentDTO pc960401Component = componentMap.get(PC960401);// componente 960401
        PaymentComponentDTO promoComponent = componentMap.get("promo");
        PaymentComponentDTO theoreticalSalaryComponent = createTheoreticalSalaryComponent(pc960400Component, pc960401Component, period, range);
        ParametersDTO promotionParameter = getParametersById(parameters, 38);
        double percentPromotion = 0.25;
        if (promotionParameter != null) percentPromotion = promotionParameter.getValue() / 100;
        //AA15*(1+SI($M4="emp";AB$2;AB$3)+AB$5)*(1+SI($W4<>"";SI($W4<=AB$11;SI(MES($W4)=MES(AB$11);$Z$6;0);0);0))
        if (theoreticalSalaryComponent.getProjections() != null) {
            for (int i = 0; i < theoreticalSalaryComponent.getProjections().size(); i++) {
                double amount = i == 0 ? theoreticalSalaryComponent.getProjections().get(i).getAmount().doubleValue() : theoreticalSalaryComponent.getProjections().get(i - 1).getAmount().doubleValue();
                double promoAmount;
                double total;
                if (promoComponent != null && promoComponent.getAmountString() != null && !promoComponent.getAmountString().isEmpty()){
                    DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                            .appendPattern(TYPEMONTH)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter();
                    LocalDate promoDate;
                    //log.info("{}", promoComponent.getAmountString());
                    try {
                        promoDate = LocalDate.parse(promoComponent.getAmountString(), dateFormat);
                    } catch (DateTimeParseException e) {
                        int excelDate = Integer.parseInt(promoComponent.getAmountString());
                        promoDate = LocalDate.of(1900, 1, 1).plusDays(excelDate - 2);
                    }
                    //log.info("{}", promoDate);
                    LocalDate date = LocalDate.parse(theoreticalSalaryComponent.getProjections().get(i).getMonth(), dateFormat);
                    // compara fechas SI($W4<=AC$11;SI(MES($W4)=MES(AC$11);$Z$6;0);0)
                    // $W4 = promoDate
                    // AC$11 = date
                    //log.info("{}", promoDate);
                    //log.info("{}", date);
                    promoDate = promoDate.withDayOfMonth(1);
                    if (promoDate.isEqual(date)) {
                        promoAmount = percentPromotion;
                    }else {
                        promoAmount = 0.0;
                    }
                }else {
                    promoAmount = 0.0;
                }
                total = amount * (1 + promoAmount);
                theoreticalSalaryComponent.getProjections().get(i).setAmount(BigDecimal.valueOf(total));
            }
        }
        component.add(theoreticalSalaryComponent);
    }
   private void processRevisionSalaryOperation(List<PaymentComponentDTO> component, List<ParametersDTO> salaryParametersList, List<ParametersDTO> revSalParametersList) {
    Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
    PaymentComponentDTO salaryComponent = componentMap.get(THEORETICAL_SALARY);
    if (salaryComponent != null){
        DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                .appendPattern(TYPEMONTH)
                .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                .toFormatter();

        Map<String, Double> salaryParametersMap = new HashMap<>();
        for (ParametersDTO salaryParameters : salaryParametersList) {
            LocalDate date1 = LocalDate.parse(salaryParameters.getPeriod(), dateFormat);
            double percentRevEMP = salaryParameters.getValue() / 100;
            salaryParametersMap.merge(date1.toString(), percentRevEMP, Double::sum);
        }

        Map<String, Double> revSalParametersMap = new HashMap<>();
        for (ParametersDTO revSalParameters : revSalParametersList) {
            LocalDate date3 = LocalDate.parse(revSalParameters.getPeriod(), dateFormat);
            double percentRev2 = revSalParameters.getValue() / 100;
            revSalParametersMap.merge(date3.toString(), percentRev2, Double::sum);
        }

        for (MonthProjection theoreticalSalaryProjection : salaryComponent.getProjections()) {
            double theoreticalSalary = theoreticalSalaryProjection.getAmount().doubleValue();
            LocalDate date2 = LocalDate.parse(theoreticalSalaryProjection.getMonth(), dateFormat);
            double revisionSalaryEMP = salaryParametersMap.getOrDefault(date2.toString(), 0.0);
            double revisionSalary2 = revSalParametersMap.getOrDefault(date2.toString(), 0.0);
            double percent = revisionSalaryEMP + revisionSalary2;
            theoreticalSalaryProjection.setAmount(BigDecimal.valueOf(Math.round((theoreticalSalary * (1 + percent)) * 100d) / 100d));
        }
    }
}
    private Map<String, PaymentComponentDTO> createComponentMap(List<PaymentComponentDTO> component) {
        return component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> {
                    existing.getProjections().addAll(replacement.getProjections());
                    return existing;
                }));
    }
    private PaymentComponentDTO createTheoreticalSalaryComponent(PaymentComponentDTO pc960400Component, PaymentComponentDTO pc960401Component, String period, Integer range) {
        PaymentComponentDTO theoreticalSalaryComponent = new PaymentComponentDTO();
        theoreticalSalaryComponent.setPaymentComponent(THEORETICAL_SALARY);
        // Calcular el valor de THEORETICAL-SALARY a partir de PC960400 y PC960401
        if (pc960400Component != null && pc960401Component != null) {
            double baseSalary = pc960400Component.getAmount().doubleValue();
            double baseSalaryIntegral = pc960401Component.getAmount().doubleValue();
            theoreticalSalaryComponent.setAmount(BigDecimal.valueOf(Math.max(baseSalary, baseSalaryIntegral)));
            theoreticalSalaryComponent.setProjections(Shared.generateMonthProjection(period, range, theoreticalSalaryComponent.getAmount()));
        }
        return theoreticalSalaryComponent;
    }
    private List<ParametersDTO> getListParametersById(List<ParametersDTO> parameters, int id) {
        return parameters.stream()
                .filter(p -> p.getParameter().getId() == id)
                .collect(Collectors.toList());
    }
    private ParametersDTO getParametersById(List<ParametersDTO> parameters, int id) {
        return parameters.stream()
                .filter(p -> p.getParameter().getId() == id)
                .findFirst()
                .orElse(null);
    }
    private void updateSalaryComponent(PaymentComponentDTO salaryComponent, ParametersDTO salaryParameters, ParametersDTO revSalParameters) {
       // double revisionSalary = 0.0;
        if (salaryComponent != null){
            for (MonthProjection theoreticalSalaryProjection : salaryComponent.getProjections()) {
                double theoreticalSalary = theoreticalSalaryProjection.getAmount().doubleValue();
                DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                        .appendPattern(TYPEMONTH)
                        .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                        .toFormatter();
                LocalDate date2 = LocalDate.parse(theoreticalSalaryProjection.getMonth(), dateFormat);
                double revisionSalaryEMP = 0.0;
                double revisionSalary2 = 0.0;
                if (salaryParameters != null) {
                    double percentRevEMP = salaryParameters.getValue() / 100;
                    LocalDate date1 = LocalDate.parse(salaryParameters.getPeriod(), dateFormat);
                    if (date1.isBefore(date2) || date1.isEqual(date2)) {
                        revisionSalaryEMP = percentRevEMP;
                    }
                }
               // log.info("{}", revSalParameters);
                if (revSalParameters != null) {
                    LocalDate date3 = LocalDate.parse(revSalParameters.getPeriod(), dateFormat);
                    double percentRev2 = revSalParameters.getValue() / 100;
                    if (date3.isBefore(date2) || date3.equals(date2)){
                        revisionSalary2 = percentRev2;
                    }
                }
                double percent = revisionSalaryEMP + revisionSalary2;
                theoreticalSalaryProjection.setAmount(BigDecimal.valueOf(Math.round((theoreticalSalary * (1 + percent)) * 100d) / 100d));
            }
        }
    }
}
