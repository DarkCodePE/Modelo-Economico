package ms.hispam.budget.config;

import ms.hispam.budget.dto.UploadStorageDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "STORAGE", url = "${feignClient.apimngr}")
public interface ApimngrFiegnClient {
    @PostMapping(value = "/storagemanagement/v1/api/upload-public", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UploadStorageDTO uploadPublicFG(@RequestHeader(value = "Content-type") String contentType,
                                    @RequestHeader String token,
                                    @RequestHeader(value = ("Ocp-Apim-Subscription-Key")) String subscription,
                                    @RequestParam Integer idUser,
                                    @RequestPart MultipartFile file);

    @PostMapping(value = "/storagemanagement/v1/api/upload-private", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UploadStorageDTO uploadPrivateFC(@RequestHeader(value = "Content-type") String contentType,
                                     @RequestHeader String token,
                                     @RequestHeader(value = ("Ocp-Apim-Subscription-Key")) String subscription,
                                     @RequestParam String container,
                                     @RequestParam Integer idUser,
                                     @RequestPart MultipartFile file);
}
