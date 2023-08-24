package ms.hispam.budget.controller;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.service.ProjectionService;
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
        replaceSLash(projection);

        return service.getProjection(projection);
    }

    private void replaceSLash( ParametersByProjection projection) {
        projection.setPeriod(projection.getPeriod().replace("/",""));
        projection.setNominaFrom(projection.getNominaFrom().replace("/",""));
        projection.setNominaTo(projection.getNominaTo().replace("/",""));
        projection.getParameters().forEach(k-> {
           k.setPeriod(k.getPeriod().replaceAll("/",""));
            k.setRange(k.getRange().replaceAll("/",""));
          k.setPeriodRetroactive(k.getPeriodRetroactive().replaceAll("/",""));
        });
    }

    @GetMapping("/componentByCountry")
    public Config getComponentBy(@RequestParam String bu) {
        return service.getComponentByBu(bu);
    }

    @PostMapping("/save-projection")
    public Response<Boolean> saveProjection(@RequestBody ParameterHistorial projection ,@RequestHeader String email) {
        projection.setPeriod(projection.getPeriod().replace("/",""));

        return service.saveProjection(projection,email);
    }
    @GetMapping("/historial")
    public List<HistorialProjectionDTO> getHistoricalProjection(@RequestHeader String email) {
        return service.getHistorial(email);
    }

    @GetMapping("/get-projection-historical")
    public Response<Page<ProjectionDTO>> getHistorialProjection(@RequestParam Integer id,@RequestParam Integer page,@RequestParam Integer size) {
        return service.getHistorialProjection(id,page,size);
    }

    @DeleteMapping("/historial")
    public Response<Boolean> deleteHistorical(@RequestParam Integer id) {
        return service.deleteHistorical(id);
    }
    @PostMapping("/download-projection")
    public ResponseEntity<byte[]> downloadHistorical(@RequestBody ParametersByProjection projection) {
        replaceSLash(projection);
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


}
