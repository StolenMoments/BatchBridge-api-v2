package org.jh.batchbridge.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.dto.ApiResponse;
import org.jh.batchbridge.dto.request.BatchCreateRequest;
import org.jh.batchbridge.dto.response.BatchDetailResponse;
import org.jh.batchbridge.dto.response.BatchListResponse;
import org.jh.batchbridge.dto.response.BatchSubmitResponse;
import org.jh.batchbridge.dto.response.ModelResponse;
import org.jh.batchbridge.service.BatchService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BatchController {

    private final BatchService batchService;
    private final List<ModelResponse> supportedModels;

    public BatchController(BatchService batchService, ModelListProperties modelListProperties) {
        this.batchService = batchService;
        this.supportedModels = modelListProperties.getSupportedModels();
    }

    @PostMapping("/batches")
    public ResponseEntity<ApiResponse<BatchDetailResponse>> createBatch(@Valid @RequestBody BatchCreateRequest request) {
        BatchDetailResponse response = batchService.createBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/batches")
    public ApiResponse<BatchListResponse> getList(
            @RequestParam(required = false) BatchStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        BatchListResponse response = batchService.getList(status, page, size);
        return ApiResponse.success(response);
    }

    @GetMapping("/batches/{id}")
    public ApiResponse<BatchDetailResponse> getDetail(@PathVariable Long id) {
        BatchDetailResponse response = batchService.getDetail(id);
        return ApiResponse.success(response);
    }

    @PostMapping("/batches/{id}/submit")
    public ResponseEntity<ApiResponse<BatchSubmitResponse>> submitBatch(@PathVariable Long id) {
        BatchSubmitResponse response = batchService.submitBatch(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/batches/{id}/sync")
    public ApiResponse<BatchDetailResponse> syncStatus(@PathVariable Long id) {
        BatchDetailResponse response = batchService.syncStatus(id);
        return ApiResponse.success(response);
    }

    @GetMapping("/models")
    public ApiResponse<List<ModelResponse>> getModels() {
        return ApiResponse.success(supportedModels);
    }

    @Component
    @ConfigurationProperties(prefix = "batch-bridge")
    public static class ModelListProperties {
        private List<ModelResponse> supportedModels;

        public List<ModelResponse> getSupportedModels() {
            return supportedModels;
        }

        public void setSupportedModels(List<ModelResponse> supportedModels) {
            this.supportedModels = supportedModels;
        }
    }
}
