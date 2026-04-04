package org.jh.batchbridge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.dto.ApiResponse;
import org.jh.batchbridge.dto.request.external.GithubPreviewRequest;
import org.jh.batchbridge.dto.request.external.JiraPreviewRequest;
import org.jh.batchbridge.dto.response.external.ExternalContextPreviewResponse;
import org.jh.batchbridge.service.ExternalContextService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/external-context")
@RequiredArgsConstructor
@Tag(name = "External Context", description = "External context management APIs")
public class ExternalContextController {

    private final ExternalContextService externalContextService;

    @PostMapping("/preview/github")
    @Operation(summary = "Preview GitHub content", description = "Fetch file content from GitHub.")
    public ApiResponse<ExternalContextPreviewResponse> previewGithub(@RequestBody @Valid GithubPreviewRequest request) {
        ExternalContextPreviewResponse response = externalContextService.previewGithub(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/preview/jira")
    @Operation(summary = "Preview JIRA content", description = "Fetch issue details from JIRA.")
    public ApiResponse<ExternalContextPreviewResponse> previewJira(@RequestBody @Valid JiraPreviewRequest request) {
        ExternalContextPreviewResponse response = externalContextService.previewJira(request);
        return ApiResponse.success(response);
    }
}
