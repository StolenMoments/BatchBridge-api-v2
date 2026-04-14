package org.jh.batchbridge.service;

import org.jh.batchbridge.exception.MediaNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    private final String storagePath;
    private final RestClient restClient;

    public MediaStorageService(
            @Value("${batch-bridge.media.storage-path:/app/media}") String storagePath,
            RestClient.Builder restClientBuilder) {
        this.storagePath = storagePath;
        this.restClient = restClientBuilder.build();
    }

    public String download(Long batchId, Long promptId, String url) {
        log.debug("Downloading media [batchId={}, promptId={}, url={}]", batchId, promptId, url);

        ResponseEntity<byte[]> response = restClient.get()
                .uri(url)
                .retrieve()
                .toEntity(byte[].class);

        String ext = resolveExtension(response.getHeaders().getContentType());

        Path dir = Paths.get(storagePath, String.valueOf(batchId));
        Path filePath = dir.resolve(promptId + "." + ext);

        byte[] body = response.getBody();
        if (body == null) {
            log.error("Empty response body [batchId={}, promptId={}, url={}]", batchId, promptId, url);
            throw new IllegalStateException(
                    "Empty response body when downloading media for batch=" + batchId + ", prompt=" + promptId + ", url=" + url);
        }

        try {
            Files.createDirectories(dir);
            Files.write(filePath, body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        log.info("Media saved [path={}]", filePath.toAbsolutePath());
        return filePath.toAbsolutePath().toString();
    }

    public Path getFilePath(Long batchId, Long promptId) {
        Path dir = Paths.get(storagePath, String.valueOf(batchId));

        try (Stream<Path> stream = Files.find(dir, 1,
                (path, attrs) -> attrs.isRegularFile() &&
                        path.getFileName().toString().matches(promptId + "\\..*"))) {
            return stream.findFirst()
                    .orElseThrow(() -> new MediaNotFoundException(batchId, promptId));
        } catch (NoSuchFileException e) {
            throw new MediaNotFoundException(batchId, promptId);
        } catch (IOException e) {
            log.error("I/O error while looking up media file [batchId={}, promptId={}]", batchId, promptId, e);
            throw new UncheckedIOException(e);
        }
    }

    private String resolveExtension(MediaType contentType) {
        if (contentType == null) {
            return "png";
        }
        String type = contentType.getType();
        String subtype = contentType.getSubtype();
        return switch (type) {
            case "image" -> switch (subtype) {
                case "jpeg", "jpg" -> "jpg";
                case "png"         -> "png";
                case "gif"         -> "gif";
                case "svg+xml"     -> "svg";
                case "webp"        -> "webp";
                default            -> "png";
            };
            case "video" -> switch (subtype) {
                case "mp4"  -> "mp4";
                case "webm" -> "webm";
                default     -> "mp4";
            };
            default -> "png";
        };
    }
}
