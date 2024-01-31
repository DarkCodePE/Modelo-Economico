package ms.hispam.budget.util;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ByteArrayMultipartFile extends ByteArrayResource implements MultipartFile {

    private final String originalFilename;
    private final String contentType;

    public ByteArrayMultipartFile(byte[] byteArray, String originalFilename, String contentType) {
        super(byteArray);
        this.originalFilename = originalFilename;
        this.contentType = contentType;
    }

    @Override
    public String getName() {
        return this.originalFilename;
    }

    @Override
    public String getOriginalFilename() {
        return this.originalFilename;
    }

    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public boolean isEmpty() {
        return this.getByteArray().length == 0;
    }

    @Override
    public long getSize() {
        return this.getByteArray().length;
    }

    @Override
    public byte[] getBytes() throws IOException {
        return this.getByteArray();
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        Files.write(dest.toPath(), this.getByteArray());
    }
}
