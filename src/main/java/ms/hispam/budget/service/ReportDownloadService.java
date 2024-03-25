package ms.hispam.budget.service;

import ms.hispam.budget.entity.mysql.ReportJob;
import ms.hispam.budget.repository.mysql.ReportJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class ReportDownloadService {
    private static final String API_DOWNLOAD_FILE = "https://apimngr-hispam-prod.azure-api.net/storagemanagement/v1/api/download";
    @Value("${api.token}")
    private String token;
    @Value("${api.subscription.key}")
    private String subscriptionKey;
    @Value("${api.container}")
    private String container;
    private final ReportJobRepository reportJobRepository;

    public ReportDownloadService(ReportJobRepository reportJobRepository) {
        this.reportJobRepository = reportJobRepository;
    }

    public byte[] getFile(String name) {
        String url = API_DOWNLOAD_FILE + "?file=" + name + "&container=hispamjob";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", subscriptionKey);
        headers.set("token", token);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        } else {
            throw new RuntimeException("Failed to download file");
        }
    }
    // Método para obtener el reporte generado
    @Transactional("mysqlTransactionManager")
    public List<ReportJob> getReport(String mail) {
        return reportJobRepository.findByIdSsffOrderByCreationDateDesc(mail);
    }
    // Método para obtener el reporte generado por usuario y BU
    @Transactional(value = "mysqlTransactionManager", readOnly = true)
    public List<ReportJob> getReportBu(String mail, Integer bu) {
        return reportJobRepository.findByIdSsffAndBu(mail, bu);
    }
}
