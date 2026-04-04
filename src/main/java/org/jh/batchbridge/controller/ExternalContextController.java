package org.jh.batchbridge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.dto.ApiResponse;
import org.jh.batchbridge.dto.request.external.GithubPreviewRequest;
import org.jh.batchbridge.dto.request.external.JiraPreviewRequest;
import org.jh.batchbridge.dto.response.external.ExternalContextPreviewResponse;
import org.jh.batchbridge.dto.response.external.ToastMessage;
import org.jh.batchbridge.service.ExternalContextService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/external-context")
@RequiredArgsConstructor
@Tag(name = "External Context", description = "External context management APIs")
public class ExternalContextController {

    private final ExternalContextService externalContextService;
    private final MessageSource messageSource;

    @PostMapping("/preview/github")
    @Operation(summary = "Preview GitHub content", description = "Fetch file content from GitHub.")
    public ApiResponse<ExternalContextPreviewResponse> previewGithub(@RequestBody @Valid GithubPreviewRequest request) {
        ExternalContextPreviewResponse preview = externalContextService.previewGithub(request);
        return ApiResponse.success(withSuccessToast(preview));
    }

    @PostMapping("/preview/jira")
    @Operation(summary = "Preview JIRA content", description = "Fetch issue details from JIRA.")
    public ApiResponse<ExternalContextPreviewResponse> previewJira(@RequestBody @Valid JiraPreviewRequest request) {
        ExternalContextPreviewResponse preview = externalContextService.previewJira(request);
        return ApiResponse.success(withSuccessToast(preview));
    }

    private ExternalContextPreviewResponse withSuccessToast(ExternalContextPreviewResponse preview) {
        Locale locale = LocaleContextHolder.getLocale();
        String title = messageSource.getMessage("toast.context.success", null, locale);
        String desc = messageSource.getMessage("toast.context.success.desc", new Object[]{1}, locale);
        return new ExternalContextPreviewResponse(preview.label(), preview.content(), new ToastMessage(title, desc));
    }
}
