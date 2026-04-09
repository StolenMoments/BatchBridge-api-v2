package org.jh.batchbridge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.dto.ApiResponse;
import org.jh.batchbridge.dto.request.PromptTemplateCreateRequest;
import org.jh.batchbridge.dto.request.PromptTemplateUpdateRequest;
import org.jh.batchbridge.dto.response.PromptTemplateResponse;
import org.jh.batchbridge.service.PromptTemplateService;
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
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Tag(name = "Prompt Template", description = "Prompt template management APIs")
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @GetMapping
    @Operation(summary = "List prompt templates", description = "List all prompt templates ordered by creation date descending.")
    public ResponseEntity<ApiResponse<List<PromptTemplateResponse>>> getTemplates() {
        return ResponseEntity.ok(ApiResponse.success(promptTemplateService.getAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get prompt template", description = "Get a single prompt template by ID.")
    public ResponseEntity<ApiResponse<PromptTemplateResponse>> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(promptTemplateService.getOne(id)));
    }

    @PostMapping
    @Operation(summary = "Create prompt template", description = "Create a new prompt template.")
    public ResponseEntity<ApiResponse<PromptTemplateResponse>> createTemplate(
            @RequestBody @Valid PromptTemplateCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(promptTemplateService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update prompt template", description = "Update an existing prompt template.")
    public ResponseEntity<ApiResponse<PromptTemplateResponse>> updateTemplate(
            @PathVariable Long id,
            @RequestBody @Valid PromptTemplateUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(promptTemplateService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete prompt template", description = "Delete a prompt template.")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        promptTemplateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
