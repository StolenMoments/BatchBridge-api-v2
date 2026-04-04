package org.jh.batchbridge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.dto.ApiResponse;
import org.jh.batchbridge.dto.request.external.ContextPreviewRequest;
import org.jh.batchbridge.dto.response.external.ContextPreviewResponse;
import org.jh.batchbridge.service.ExternalContextService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
@Tag(name = "External Context", description = "External context import APIs")
public class ExternalContextController {

    private final ExternalContextService externalContextService;

    @PostMapping("/preview")
    @Operation(summary = "Preview external context", description = "Fetch content from GitHub PR, Jira issues, and Confluence pages.")
    public ApiResponse<ContextPreviewResponse> preview(@RequestBody ContextPreviewRequest request) {
        return ApiResponse.success(externalContextService.preview(request));
    }
}
