package org.jh.batchbridge.service;

import org.jh.batchbridge.exception.MediaNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Component
public class MediaStorageService {

    private final String storagePath;
    private final RestClient restClient;

    public MediaStorageService(
            @Value("${batch-bridge.media.storage-path:/app/media}") String storagePath,
            RestClient.Builder restClientBuilder) {
        this.storagePath = storagePath;
        this.restClient = restClientBuilder.build();
    }

    public String download(Long batchId, Long promptId, String url) {
        ResponseEntity<byte[]> response = restClient.get()
                .uri(url)
                .retrieve()
                .toEntity(byte[].class);

        String ext = resolveExtension(response.getHeaders().getContentType());

        Path dir = Paths.get(storagePath, String.valueOf(batchId));
        Path filePath = dir.resolve(promptId + "." + ext);

        try {
            Files.createDirectories(dir);
            Files.write(filePath, response.getBody());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return filePath.toAbsolutePath().toString();
    }

    public Path getFilePath(Long batchId, Long promptId) {
        Path dir = Paths.get(storagePath, String.valueOf(batchId));
        String glob = promptId + ".*";

        try (Stream<Path> stream = Files.find(dir, 1,
                (path, attrs) -> attrs.isRegularFile() &&
                        path.getFileName().toString().matches(promptId + "\\..*"))) {
            return stream.findFirst()
                    .orElseThrow(() -> new MediaNotFoundException(batchId, promptId));
        } catch (IOException e) {
            throw new MediaNotFoundException(batchId, promptId);
        }
    }

    private String resolveExtension(MediaType contentType) {
        if (contentType == null) {
            return "png";
        }
        String type = contentType.getType();
        String subtype = contentType.getSubtype();
        return switch (type) {
            case "image" -> subtype.equals("*") ? "png" : subtype;
            case "video" -> subtype.equals("*") ? "mp4" : subtype;
            default -> "png";
        };
    }
}
