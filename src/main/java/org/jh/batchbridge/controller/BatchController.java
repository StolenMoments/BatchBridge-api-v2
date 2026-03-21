package org.jh.batchbridge.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.dto.ApiResponse;
import org.jh.batchbridge.dto.request.BatchCreateRequest;
import org.jh.batchbridge.dto.response.BatchDetailResponse;
import org.jh.batchbridge.dto.response.BatchListResponse;
import org.jh.batchbridge.dto.response.BatchSubmitResponse;
import org.jh.batchbridge.service.BatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Batch", description = "Batch management APIs")
@Validated
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping("/batches")
    @Operation(summary = "Create batch", description = "Create a new batch.")
    public ResponseEntity<ApiResponse<BatchDetailResponse>> createBatch(@Valid @RequestBody BatchCreateRequest request) {
        BatchDetailResponse response = batchService.createBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/batches")
    @Operation(summary = "List batches", description = "List batches with status and paging filters.")
    public ApiResponse<BatchListResponse> getList(
            @RequestParam(required = false) BatchStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        BatchListResponse response = batchService.getList(status, page, size);
        return ApiResponse.success(response);
    }

    @GetMapping("/batches/{id}")
    @Operation(summary = "Get batch detail", description = "Get batch details by batch ID.")
    public ApiResponse<BatchDetailResponse> getDetail(@PathVariable Long id) {
        BatchDetailResponse response = batchService.getDetail(id);
        return ApiResponse.success(response);
    }

    @PostMapping("/batches/{id}/submit")
    @Operation(summary = "Submit batch", description = "Submit a draft batch to the external API.")
    public ResponseEntity<ApiResponse<BatchSubmitResponse>> submitBatch(@PathVariable Long id) {
        BatchSubmitResponse response = batchService.submitBatch(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/batches/{id}/sync")
    @Operation(summary = "Sync batch status", description = "Sync latest batch status from external API.")
    public ApiResponse<BatchDetailResponse> syncStatus(@PathVariable Long id) {
        BatchDetailResponse response = batchService.syncStatus(id);
        return ApiResponse.success(response);
    }
}
