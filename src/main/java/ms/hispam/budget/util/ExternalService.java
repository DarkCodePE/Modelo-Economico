package ms.hispam.budget.util;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j(topic = "ExternalService")
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
    public UploadStorageDTO uploadFilePrivate(Integer idUser, MultipartFile file) {

        String token = "ea3967e98d9f69955345a2d9c52151080a23c9a71b7f2f66c1ed20378560705a7ca0800faf0bf23638c52c9f455fb0de23917d4475a44aa676ad47a26696b22d";
        String subscription = "f60aac663e674ad1a899993ae09c41e9";
        String container = "modelo-economico";

        return apimngrFG.uploadPrivateFC("multipart/form-data", token, subscription, container, idUser, file);
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
    public String uploadProjectionFile(Integer userId, byte[] data, String fileName, int version) {
        try {
            String versionedFileName = fileName + "_v" + version + ".gz";
            MultipartFile multipartFile = new ByteArrayMultipartFile(data, versionedFileName, "application/octet-stream");
            UploadStorageDTO response = uploadFilePublic(userId, multipartFile);
            return response.getUrl();
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Error al subir el archivo de la proyección", e);
        }
    }
    public byte[] downloadProjectionFile(String fileUrl) {
        try {
            // Registrar el valor de fileUrl
            log.debug("Intentando descargar el archivo desde la URL: {}", fileUrl);
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
