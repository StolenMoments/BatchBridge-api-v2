package org.jh.batchbridge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.dto.ApiResponse;
import org.jh.batchbridge.dto.response.ModelInfo;
import org.jh.batchbridge.service.ModelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Tag(name = "Model", description = "Model management APIs")
public class ModelController {
    private final ModelService modelService;

    @GetMapping
    @Operation(summary = "List supported models", description = "List models available for batch creation.")
    public ResponseEntity<ApiResponse<List<ModelInfo>>> getModels() {
        return ResponseEntity.ok(ApiResponse.success(modelService.getAllModels()));
    }
}
