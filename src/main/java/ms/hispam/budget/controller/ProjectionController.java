package ms.hispam.budget.controller;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.service.ProjectionService;
import ms.hispam.budget.util.Shared;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("budget/v1")
@CrossOrigin("*")
public class ProjectionController {

    @Autowired
    private ProjectionService service;
    @PostMapping("/projection")
    public Response<Page<ProjectionDTO>> getProjection(@RequestBody ParametersByProjection projection) {
        Shared.replaceSLash(projection);

        return service.getProjection(projection);
    }

    @PostMapping("/data-base")
    public DataBaseMainReponse getDataBase(@RequestBody DataRequest dataRequest) {

        return service.getDataBase(dataRequest);
    }
    @GetMapping("/bu")
    public List<Bu> accessBu(@RequestHeader String user) {
        return service.findByBuAccess(user);
    }
    @GetMapping("/componentByCountry")
    public Config getComponentBy(@RequestParam String bu) {
        return service.getComponentByBu(bu);
    }

    @PostMapping("/save-projection")
    public Response<Boolean> saveProjection(@RequestBody ParameterHistorial projection ,@RequestHeader String user) {
        projection.setPeriod(projection.getPeriod().replace("/",""));

        return service.saveProjection(projection,user);
    }
    @GetMapping("/historial")
    public List<HistorialProjectionDTO> getHistoricalProjection(@RequestHeader String user) {
        return service.getHistorial(user);
    }

    @GetMapping("/get-projection-historical")
    public ProjectionInformation getHistorialProjection(@RequestParam Integer id) {
        return service.getHistorialProjection(id);
    }

    @DeleteMapping("/historial")
    public Response<Boolean> deleteHistorical(@RequestParam Integer id) {
        return service.deleteHistorical(id);
    }
    @PostMapping("/download-projection")
    public ResponseEntity<byte[]> downloadHistorical(@RequestBody ParametersByProjection projection) {
        Shared.replaceSLash(projection);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "datos.xlsx");
        return new ResponseEntity<>(service.downloadProjection(projection), headers, 200);
    }
    @GetMapping("/download-historical")
    public ResponseEntity<byte[]> downloadProjectionHistorical(@RequestParam Integer id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "datos.xlsx");
        return new ResponseEntity<>(service.downloadProjectionHistorical(id), headers, 200);
    }

    @GetMapping("/account")
    public Response<List<AccountProjection>> getAccount(@RequestParam Integer id) {
        return service.getAccountsByBu(id);
    }
    @GetMapping("/rosseta")
    public List<RosetaDTO> getRosseta(@RequestParam Integer id) {
        return service.getRoseta(id);
    }
    @GetMapping("/save-money")
    public Boolean saveMoney(@RequestParam Integer id,@RequestParam String po) {
        return service.saveMoneyOdin(po,id);
    }

}
