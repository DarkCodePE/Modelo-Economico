    package ms.hispam.budget.service.impl;

    import static org.junit.Assert.assertEquals;
    import static org.junit.Assert.assertThrows;
    import static org.junit.Assert.assertTrue;
    import static org.mockito.Mockito.mock;
    import static org.mockito.Mockito.verify;
    import static org.mockito.Mockito.when;

    import java.time.LocalDate;
    import java.time.ZoneOffset;
    import java.util.ArrayList;
    import java.util.Date;
    import java.util.List;
    import java.util.Optional;
    import java.util.concurrent.Executor;

    import ms.hispam.budget.dto.BaseExternResponse;

    import ms.hispam.budget.dto.Config;
    import ms.hispam.budget.dto.OperationResponse;
    import ms.hispam.budget.dto.ParameterHistorial;
    import ms.hispam.budget.dto.RangeBuDTO;
    import ms.hispam.budget.entity.mysql.BaseExtern;
    import ms.hispam.budget.entity.mysql.Bu;
    import ms.hispam.budget.entity.mysql.DisabledPoHistorical;
    import ms.hispam.budget.entity.mysql.HistorialProjection;
    import ms.hispam.budget.entity.mysql.ParameterProjection;
    import ms.hispam.budget.exception.BadRequestException;
    import ms.hispam.budget.repository.mysql.*;
    import ms.hispam.budget.repository.sqlserver.ParametersRepository;
    import ms.hispam.budget.service.BuService;
    import ms.hispam.budget.service.MexicoService;
    import ms.hispam.budget.service.ReportGenerationService;
    import ms.hispam.budget.util.XlsReportService;
    import org.junit.Test;
    import org.junit.jupiter.api.extension.ExtendWith;
    import org.junit.runner.RunWith;
    import org.mockito.Mockito;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.boot.test.mock.mockito.MockBean;
    import org.springframework.test.context.ContextConfiguration;
    import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
    import org.springframework.test.context.junit.jupiter.SpringExtension;
    import org.springframework.web.server.ResponseStatusException;

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = {ProjectionServiceImpl.class})
    @RunWith(SpringJUnit4ClassRunner.class)
    public class ProjectionServiceImplDiffblueTest {

    }
