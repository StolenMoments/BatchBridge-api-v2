package org.jh.batchbridge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.exception.MediaNotFoundException;
import org.jh.batchbridge.exception.PromptNotFoundException;
import org.jh.batchbridge.repository.BatchPromptRepository;
import org.jh.batchbridge.service.MediaStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "미디어 파일 서빙 API")
public class MediaController {

    private final BatchPromptRepository batchPromptRepository;
    private final MediaStorageService mediaStorageService;

    @GetMapping("/{batchId}/{promptId}")
    @Operation(summary = "미디어 파일 다운로드", description = "배치 작업 결과로 저장된 미디어 파일을 스트리밍으로 반환합니다.")
    public ResponseEntity<Resource> getMedia(
            @PathVariable Long batchId,
            @PathVariable Long promptId) {

        BatchPrompt prompt = batchPromptRepository
                .findByIdAndBatchId(promptId, batchId)
                .orElseThrow(() -> new PromptNotFoundException(promptId));

        if (prompt.getResultMediaPath() == null) {
            throw new MediaNotFoundException(batchId, promptId);
        }

        Path filePath = mediaStorageService.getFilePath(batchId, promptId);

        String contentType = determineContentType(filePath);

        String filename = filePath.getFileName().toString();
        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(filename)
                                .build()
                                .toString())
                .body(resource);
    }

    private String determineContentType(Path filePath) {
        try {
            String probed = Files.probeContentType(filePath);
            return probed != null ? probed : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}
