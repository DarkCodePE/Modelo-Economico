package ms.hispam.budget.util;

import ms.hispam.budget.config.ApimngrFiegnClient;
import ms.hispam.budget.dto.FileDTO;
import ms.hispam.budget.dto.UploadStorageDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.util.Objects;

@Service
public class ExternalService {
    private final ApimngrFiegnClient apimngrFG;
    @Value("${api.token}")
    private String token;
    @Value("${api.subscription.key}")
    private String subscriptionKey;
    @Value("${api.container}")
    private String container;
    public ExternalService(ApimngrFiegnClient apimngrFG) {
        this.apimngrFG = apimngrFG;
    }
    UploadStorageDTO uploadFilePublic(Integer idUser, MultipartFile file) {
        return apimngrFG.uploadPublicFG("multipart/form-data", token, subscriptionKey, idUser, file);
    }
    public FileDTO uploadExcelReport(Integer idUser, MultipartFile file) {
        FileDTO fileProcess = null;
        if(file != null) {
            try {
                UploadStorageDTO response = uploadFilePublic(idUser, file);
                fileProcess = new FileDTO(
                        file.getOriginalFilename(),
                        response.getUrl());
            } catch (Exception e) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Error al subir el archivo", e);
            }
        }
        return fileProcess;
    }
    public String uploadProjectionFile(Integer userId, byte[] data, String fileName) {
        try {
            MultipartFile multipartFile = new ByteArrayMultipartFile(data, fileName + ".gz", "application/octet-stream");
            UploadStorageDTO response = uploadFilePublic(userId, multipartFile);
            return response.getUrl();
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Error al subir el archivo de la proyección", e);
        }
    }
    public byte[] downloadProjectionFile(String fileUrl) {
        try {
            // Example using RestTemplate
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<byte[]> response = restTemplate.getForEntity(fileUrl, byte[].class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al descargar el archivo de proyección");
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al descargar el archivo de proyección", e);
        }
    }
}
