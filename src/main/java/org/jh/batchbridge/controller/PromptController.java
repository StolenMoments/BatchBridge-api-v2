package org.jh.batchbridge.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.dto.ApiResponse;
import org.jh.batchbridge.dto.request.PromptAddRequest;
import org.jh.batchbridge.dto.request.PromptUpdateRequest;
import org.jh.batchbridge.dto.response.BatchPromptResponse;
import org.jh.batchbridge.service.PromptService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batches/{batchId}/prompts")
@RequiredArgsConstructor
@Tag(name = "Prompt", description = "Prompt management APIs")
public class PromptController {

    private final PromptService promptService;

    @PostMapping
    @Operation(summary = "Add prompt", description = "Add a prompt to a batch.")
    public ResponseEntity<ApiResponse<BatchPromptResponse>> addPrompt(
            @PathVariable Long batchId,
            @RequestBody @Valid PromptAddRequest request) {
        BatchPromptResponse response = promptService.addPrompt(batchId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PutMapping("/{promptId}")
    @Operation(summary = "Update prompt", description = "Update a prompt in a batch.")
    public ResponseEntity<ApiResponse<BatchPromptResponse>> updatePrompt(
            @PathVariable Long batchId,
            @PathVariable Long promptId,
            @RequestBody @Valid PromptUpdateRequest request) {
        BatchPromptResponse response = promptService.updatePrompt(batchId, promptId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{promptId}")
    @Operation(summary = "Delete prompt", description = "Delete a prompt from a batch.")
    public ResponseEntity<Void> deletePrompt(
            @PathVariable Long batchId,
            @PathVariable Long promptId) {
        promptService.deletePrompt(batchId, promptId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{promptId}")
    @Operation(summary = "Get prompt", description = "Get a prompt in a batch.")
    public ResponseEntity<ApiResponse<BatchPromptResponse>> getPrompt(
            @PathVariable Long batchId,
            @PathVariable Long promptId) {
        BatchPromptResponse response = promptService.getPrompt(batchId, promptId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
