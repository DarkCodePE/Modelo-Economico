package ms.hispam.budget.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ms.hispam.budget.dto.Config;
import ms.hispam.budget.dto.OperationResponse;
import ms.hispam.budget.dto.RangeBuDTO;
import ms.hispam.budget.entity.mysql.BaseExtern;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.exception.BadRequestException;
import ms.hispam.budget.repository.mysql.BaseExternRepository;
import ms.hispam.budget.repository.mysql.BuRepository;
import ms.hispam.budget.repository.mysql.BusinessCaseRepository;
import ms.hispam.budget.repository.mysql.CodeNominaRepository;
import ms.hispam.budget.repository.mysql.DaysVacationOfTimeRepository;
import ms.hispam.budget.repository.mysql.DemoRepository;
import ms.hispam.budget.repository.mysql.DisabledPoHistorialRepository;
import ms.hispam.budget.repository.mysql.FrecuentlyRepository;
import ms.hispam.budget.repository.mysql.HistorialProjectionRepository;
import ms.hispam.budget.repository.mysql.JsonDatosRepository;
import ms.hispam.budget.repository.mysql.LegalEntityRepository;
import ms.hispam.budget.repository.mysql.ParameterDefaultRepository;
import ms.hispam.budget.repository.mysql.ParameterProjectionRepository;
import ms.hispam.budget.repository.mysql.ParameterRepository;
import ms.hispam.budget.repository.mysql.PoHistorialExternRepository;
import ms.hispam.budget.repository.mysql.RangeBuPivotRepository;
import ms.hispam.budget.repository.mysql.TypEmployeeRepository;
import ms.hispam.budget.repository.sqlserver.ParametersRepository;
import ms.hispam.budget.service.BuService;
import ms.hispam.budget.service.MexicoService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.server.ResponseStatusException;

@ContextConfiguration(classes = {ProjectionServiceImpl.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class ProjectionServiceImplDiffblueTest {
    @MockBean
    private BaseExternRepository baseExternRepository;

    @MockBean
    private BuRepository buRepository;

    @MockBean
    private BuService buService;

    @MockBean
    private BusinessCaseRepository businessCaseRepository;

    @MockBean
    private CodeNominaRepository codeNominaRepository;

    @MockBean
    private DaysVacationOfTimeRepository daysVacationOfTimeRepository;

    @MockBean
    private DemoRepository demoRepository;

    @MockBean
    private DisabledPoHistorialRepository disabledPoHistorialRepository;

    @MockBean
    private FrecuentlyRepository frecuentlyRepository;

    @MockBean
    private HistorialProjectionRepository historialProjectionRepository;

    @MockBean
    private JsonDatosRepository jsonDatosRepository;

    @MockBean
    private LegalEntityRepository legalEntityRepository;

    @MockBean
    private MexicoService mexicoService;

    @MockBean
    private ParameterDefaultRepository parameterDefaultRepository;

    @MockBean
    private ParameterProjectionRepository parameterProjectionRepository;

    @MockBean
    private ParameterRepository parameterRepository;

    @MockBean
    private ParametersRepository parametersRepository;

    @MockBean
    private PoHistorialExternRepository poHistorialExternRepository;

    @Autowired
    private ProjectionServiceImpl projectionServiceImpl;

    @MockBean
    private RangeBuPivotRepository rangeBuPivotRepository;

    @MockBean
    private TypEmployeeRepository typEmployeeRepository;

    /**
     * Method under test: {@link ProjectionServiceImpl#getComponentByBu(String)}
     */
    @Test
    public void testGetComponentByBu() {
        // Arrange
        ArrayList<BaseExtern> baseExternList = new ArrayList<>();
        when(baseExternRepository.findByBu(Mockito.<Integer>any())).thenReturn(baseExternList);

        Bu bu = new Bu();
        bu.setBu("Bu");
        bu.setCurrent("Current");
        bu.setFlag("Flag");
        bu.setIcon("Icon");
        bu.setId(1);
        bu.setMoney("Money");
        bu.setVViewPo(true);
        Optional<Bu> ofResult = Optional.of(bu);
        when(buRepository.findByBu(Mockito.<String>any())).thenReturn(ofResult);
        when(buService.getAllBuWithRangos(Mockito.<Integer>any())).thenReturn(new ArrayList<>());
        when(codeNominaRepository.findByIdBu(Mockito.<Integer>any())).thenReturn(new ArrayList<>());
        when(parameterDefaultRepository.findByBu(Mockito.<Integer>any())).thenReturn(new ArrayList<>());
        when(parameterRepository.getParameterBu(Mockito.<String>any())).thenReturn(new ArrayList<>());
        when(demoRepository.getComponentByBu(Mockito.<String>any())).thenReturn(new ArrayList<>());

        // Act
        Config actualComponentByBu = projectionServiceImpl.getComponentByBu("Bu");

        // Assert
        verify(baseExternRepository).findByBu(Mockito.<Integer>any());
        verify(buRepository).findByBu(Mockito.<String>any());
        verify(codeNominaRepository).findByIdBu(Mockito.<Integer>any());
        verify(demoRepository).getComponentByBu(Mockito.<String>any());
        verify(parameterDefaultRepository).findByBu(Mockito.<Integer>any());
        verify(parameterRepository).getParameterBu(Mockito.<String>any());
        verify(buService).getAllBuWithRangos(Mockito.<Integer>any());
        assertEquals("Current", actualComponentByBu.getCurrent());
        assertEquals("Icon", actualComponentByBu.getIcon());
        assertEquals("Money", actualComponentByBu.getMoney());
        assertTrue(actualComponentByBu.getVViewPo());
        assertEquals(baseExternList, actualComponentByBu.getBaseExtern());
        assertEquals(baseExternList, actualComponentByBu.getComponents());
        assertEquals(baseExternList, actualComponentByBu.getNominas());
        assertEquals(baseExternList, actualComponentByBu.getParameters());
        assertEquals(baseExternList, actualComponentByBu.getVDefault());
        assertEquals(baseExternList, actualComponentByBu.getVTemporal());
    }

    /**
     * Method under test: {@link ProjectionServiceImpl#getComponentByBu(String)}
     */
    @Test
    public void testGetComponentByBu2() {
        // Arrange
        Bu bu = new Bu();
        bu.setBu("Bu");
        bu.setCurrent("Current");
        bu.setFlag("Flag");
        bu.setIcon("Icon");
        bu.setId(1);
        bu.setMoney("Money");
        bu.setVViewPo(true);
        Optional<Bu> ofResult = Optional.of(bu);
        when(buRepository.findByBu(Mockito.<String>any())).thenReturn(ofResult);
        when(demoRepository.getComponentByBu(Mockito.<String>any())).thenThrow(new BadRequestException("vbu {}"));

        // Act and Assert
        assertThrows(BadRequestException.class, () -> projectionServiceImpl.getComponentByBu("Bu"));
        verify(buRepository).findByBu(Mockito.<String>any());
        verify(demoRepository).getComponentByBu(Mockito.<String>any());
    }

    /**
     * Method under test: {@link ProjectionServiceImpl#getComponentByBu(String)}
     */
    @Test
    public void testGetComponentByBu3() {
        // Arrange
        BaseExtern baseExtern = new BaseExtern();
        baseExtern.setBu(1);
        baseExtern.setCode("vbu {}");
        baseExtern.setId(1);
        baseExtern.setName("vbu {}");

        ArrayList<BaseExtern> baseExternList = new ArrayList<>();
        baseExternList.add(baseExtern);
        when(baseExternRepository.findByBu(Mockito.<Integer>any())).thenReturn(baseExternList);

        Bu bu = new Bu();
        bu.setBu("Bu");
        bu.setCurrent("Current");
        bu.setFlag("Flag");
        bu.setIcon("Icon");
        bu.setId(1);
        bu.setMoney("Money");
        bu.setVViewPo(true);
        Optional<Bu> ofResult = Optional.of(bu);
        when(buRepository.findByBu(Mockito.<String>any())).thenReturn(ofResult);
        ArrayList<RangeBuDTO> rangeBuDTOList = new ArrayList<>();
        when(buService.getAllBuWithRangos(Mockito.<Integer>any())).thenReturn(rangeBuDTOList);
        when(codeNominaRepository.findByIdBu(Mockito.<Integer>any())).thenReturn(new ArrayList<>());
        when(parameterDefaultRepository.findByBu(Mockito.<Integer>any())).thenReturn(new ArrayList<>());
        when(parameterRepository.getParameterBu(Mockito.<String>any())).thenReturn(new ArrayList<>());
        when(demoRepository.getComponentByBu(Mockito.<String>any())).thenReturn(new ArrayList<>());

        // Act
        Config actualComponentByBu = projectionServiceImpl.getComponentByBu("Bu");

        // Assert
        verify(baseExternRepository).findByBu(Mockito.<Integer>any());
        verify(buRepository).findByBu(Mockito.<String>any());
        verify(codeNominaRepository).findByIdBu(Mockito.<Integer>any());
        verify(demoRepository).getComponentByBu(Mockito.<String>any());
        verify(parameterDefaultRepository).findByBu(Mockito.<Integer>any());
        verify(parameterRepository).getParameterBu(Mockito.<String>any());
        verify(buService).getAllBuWithRangos(Mockito.<Integer>any());
        assertEquals("Current", actualComponentByBu.getCurrent());
        assertEquals("Icon", actualComponentByBu.getIcon());
        assertEquals("Money", actualComponentByBu.getMoney());
        List<OperationResponse> baseExtern2 = actualComponentByBu.getBaseExtern();
        assertEquals(1, baseExtern2.size());
        OperationResponse getResult = baseExtern2.get(0);
        assertEquals("vbu {}", getResult.getCode());
        assertEquals("vbu {}", getResult.getName());
        List<RangeBuDTO> vTemporal = actualComponentByBu.getVTemporal();
        assertTrue(vTemporal.isEmpty());
        assertTrue(actualComponentByBu.getVViewPo());
        assertEquals(rangeBuDTOList, actualComponentByBu.getComponents());
        assertEquals(vTemporal, actualComponentByBu.getNominas());
        assertEquals(vTemporal, actualComponentByBu.getParameters());
        assertEquals(vTemporal, actualComponentByBu.getVDefault());
    }

    /**
     * Method under test: {@link ProjectionServiceImpl#getComponentByBu(String)}
     */
    @Test
    public void testGetComponentByBu4() {
        // Arrange
        BaseExtern baseExtern = new BaseExtern();
        baseExtern.setBu(1);
        baseExtern.setCode("vbu {}");
        baseExtern.setId(1);
        baseExtern.setName("vbu {}");

        BaseExtern baseExtern2 = new BaseExtern();
        baseExtern2.setBu(0);
        baseExtern2.setCode("Code");
        baseExtern2.setId(2);
        baseExtern2.setName("Name");

        ArrayList<BaseExtern> baseExternList = new ArrayList<>();
        baseExternList.add(baseExtern2);
        baseExternList.add(baseExtern);
        when(baseExternRepository.findByBu(Mockito.<Integer>any())).thenReturn(baseExternList);

        Bu bu = new Bu();
        bu.setBu("Bu");
        bu.setCurrent("Current");
        bu.setFlag("Flag");
        bu.setIcon("Icon");
        bu.setId(1);
        bu.setMoney("Money");
        bu.setVViewPo(true);
        Optional<Bu> ofResult = Optional.of(bu);
        when(buRepository.findByBu(Mockito.<String>any())).thenReturn(ofResult);
        ArrayList<RangeBuDTO> rangeBuDTOList = new ArrayList<>();
        when(buService.getAllBuWithRangos(Mockito.<Integer>any())).thenReturn(rangeBuDTOList);
        when(codeNominaRepository.findByIdBu(Mockito.<Integer>any())).thenReturn(new ArrayList<>());
        when(parameterDefaultRepository.findByBu(Mockito.<Integer>any())).thenReturn(new ArrayList<>());
        when(parameterRepository.getParameterBu(Mockito.<String>any())).thenReturn(new ArrayList<>());
        when(demoRepository.getComponentByBu(Mockito.<String>any())).thenReturn(new ArrayList<>());

        // Act
        Config actualComponentByBu = projectionServiceImpl.getComponentByBu("Bu");

        // Assert
        verify(baseExternRepository).findByBu(Mockito.<Integer>any());
        verify(buRepository).findByBu(Mockito.<String>any());
        verify(codeNominaRepository).findByIdBu(Mockito.<Integer>any());
        verify(demoRepository).getComponentByBu(Mockito.<String>any());
        verify(parameterDefaultRepository).findByBu(Mockito.<Integer>any());
        verify(parameterRepository).getParameterBu(Mockito.<String>any());
        verify(buService).getAllBuWithRangos(Mockito.<Integer>any());
        List<OperationResponse> baseExtern3 = actualComponentByBu.getBaseExtern();
        assertEquals(2, baseExtern3.size());
        OperationResponse getResult = baseExtern3.get(0);
        assertEquals("Code", getResult.getCode());
        assertEquals("Current", actualComponentByBu.getCurrent());
        assertEquals("Icon", actualComponentByBu.getIcon());
        assertEquals("Money", actualComponentByBu.getMoney());
        assertEquals("Name", getResult.getName());
        OperationResponse getResult2 = baseExtern3.get(1);
        assertEquals("vbu {}", getResult2.getCode());
        assertEquals("vbu {}", getResult2.getName());
        List<RangeBuDTO> vTemporal = actualComponentByBu.getVTemporal();
        assertTrue(vTemporal.isEmpty());
        assertTrue(actualComponentByBu.getVViewPo());
        assertEquals(rangeBuDTOList, actualComponentByBu.getComponents());
        assertEquals(rangeBuDTOList, actualComponentByBu.getVDefault());
        assertEquals(vTemporal, actualComponentByBu.getNominas());
        assertEquals(vTemporal, actualComponentByBu.getParameters());
    }

    /**
     * Method under test: {@link ProjectionServiceImpl#getComponentByBu(String)}
     */
    @Test
    public void testGetComponentByBu5() {
        // Arrange
        Optional<Bu> emptyResult = Optional.empty();
        when(buRepository.findByBu(Mockito.<String>any())).thenReturn(emptyResult);

        // Act and Assert
        assertThrows(ResponseStatusException.class, () -> projectionServiceImpl.getComponentByBu("Bu"));
        verify(buRepository).findByBu(Mockito.<String>any());
    }
}
