package org.jh.batchbridge.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.dto.ApiResponse;
import org.jh.batchbridge.dto.request.PromptAddRequest;
import org.jh.batchbridge.dto.request.PromptUpdateRequest;
import org.jh.batchbridge.dto.response.BatchPromptResponse;
import org.jh.batchbridge.service.PromptService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/batches/{batchId}/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    @PostMapping
    public ResponseEntity<ApiResponse<BatchPromptResponse>> addPrompt(
            @PathVariable Long batchId,
            @RequestBody @Valid PromptAddRequest request) {
        BatchPromptResponse response = promptService.addPrompt(batchId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PutMapping("/{promptId}")
    public ResponseEntity<ApiResponse<BatchPromptResponse>> updatePrompt(
            @PathVariable Long batchId,
            @PathVariable Long promptId,
            @RequestBody PromptUpdateRequest request) {
        BatchPromptResponse response = promptService.updatePrompt(batchId, promptId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{promptId}")
    public ResponseEntity<Void> deletePrompt(
            @PathVariable Long batchId,
            @PathVariable Long promptId) {
        promptService.deletePrompt(batchId, promptId);
        return ResponseEntity.noContent().build();
    }
}
