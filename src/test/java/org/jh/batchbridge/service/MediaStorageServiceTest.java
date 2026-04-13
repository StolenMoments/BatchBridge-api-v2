package org.jh.batchbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;
import org.jh.batchbridge.exception.MediaNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class MediaStorageServiceTest {

    @TempDir
    Path tempDir;

    private RestClient restClient;
    private RestClient.Builder restClientBuilder;
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersSpec requestHeadersSpec;
    private RestClient.ResponseSpec responseSpec;
    private MediaStorageService service;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        restClientBuilder = mock(RestClient.Builder.class);
        requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClientBuilder.build()).thenReturn(restClient);
        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(String.class));
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();

        service = new MediaStorageService(tempDir.toString(), restClientBuilder);
    }

    @Test
    void download_ImageUrl_SavesAsPng() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        ResponseEntity<byte[]> response = ResponseEntity.ok().headers(headers).body(new byte[]{1, 2, 3});
        doReturn(response).when(responseSpec).toEntity(byte[].class);

        String filePath = service.download(123L, 456L, "http://example.com/image.png");

        assertThat(filePath).endsWith("456.png");
        Path saved = Path.of(filePath);
        assertThat(saved).exists();
        assertThat(saved.getParent().getFileName().toString()).isEqualTo("123");
        assertThat(Files.readAllBytes(saved)).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void download_VideoUrl_SavesAsMp4() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("video/mp4"));
        ResponseEntity<byte[]> response = ResponseEntity.ok().headers(headers).body(new byte[]{4, 5, 6});
        doReturn(response).when(responseSpec).toEntity(byte[].class);

        String filePath = service.download(1L, 2L, "http://example.com/video.mp4");

        assertThat(filePath).endsWith("2.mp4");
        assertThat(Files.readAllBytes(Path.of(filePath))).isEqualTo(new byte[]{4, 5, 6});
    }

    @Test
    void download_NullBody_ThrowsIllegalStateException() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        ResponseEntity<byte[]> response = ResponseEntity.ok().headers(headers).body(null);
        doReturn(response).when(responseSpec).toEntity(byte[].class);

        assertThatThrownBy(() -> service.download(1L, 2L, "http://example.com/image.png"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Empty response body")
                .hasMessageContaining("http://example.com/image.png");
    }

    @Test
    void getFilePath_ExistingFile_ReturnsPath() throws IOException {
        Path dir = tempDir.resolve("10");
        Files.createDirectories(dir);
        Path file = dir.resolve("20.png");
        Files.write(file, new byte[]{1});

        Path result = service.getFilePath(10L, 20L);

        assertThat(result).isEqualTo(file);
    }

    @Test
    void getFilePath_MissingFile_ThrowsMediaNotFoundException() {
        assertThatThrownBy(() -> service.getFilePath(99L, 88L))
                .isInstanceOf(MediaNotFoundException.class)
                .hasMessageContaining("99")
                .hasMessageContaining("88");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void getFilePath_PermissionDenied_ThrowsUncheckedIOException() throws IOException {
        Path dir = tempDir.resolve("30");
        Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("---------")));

        assertThatThrownBy(() -> service.getFilePath(30L, 40L))
                .isInstanceOf(UncheckedIOException.class);
    }

    static Stream<Arguments> contentTypeToExtension() {
        // image/* / video/* 와일드카드는 HTTP Content-Type 헤더에 유효하지 않아 제외
        return Stream.of(
                Arguments.of("image/jpeg", "jpg"),
                Arguments.of("image/jpg", "jpg"),
                Arguments.of("image/png", "png"),
                Arguments.of("image/gif", "gif"),
                Arguments.of("image/svg+xml", "svg"),
                Arguments.of("video/mp4", "mp4"),
                Arguments.of("video/webm", "webm"),
                Arguments.of("application/octet-stream", "png")
        );
    }

    @ParameterizedTest(name = "Content-Type {0} → .{1}")
    @MethodSource("contentTypeToExtension")
    void download_ContentType_ResolvesCorrectExtension(String contentTypeStr, String expectedExt) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentTypeStr));
        ResponseEntity<byte[]> response = ResponseEntity.ok().headers(headers).body(new byte[]{1});
        doReturn(response).when(responseSpec).toEntity(byte[].class);

        String filePath = service.download(1L, 2L, "http://example.com/file");

        assertThat(filePath).endsWith("2." + expectedExt);
    }
}
