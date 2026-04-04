package org.jh.batchbridge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.dto.ApiResponse;
import org.jh.batchbridge.dto.request.external.ContextPreviewRequest;
import org.jh.batchbridge.dto.response.external.ContextPreviewResponse;
import org.jh.batchbridge.dto.response.external.SourceStatus;
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
@RequestMapping("/api/context")
@RequiredArgsConstructor
@Tag(name = "External Context", description = "External context import APIs")
public class ExternalContextController {

    private final ExternalContextService externalContextService;
    private final MessageSource messageSource;

    @PostMapping("/preview")
    @Operation(summary = "Preview external context", description = "Fetch content from GitHub PR, Jira issues, and Confluence pages.")
    public ApiResponse<ContextPreviewResponse> preview(@RequestBody ContextPreviewRequest request) {
        ContextPreviewResponse preview = externalContextService.preview(request);
        return ApiResponse.success(withToast(preview));
    }

    private ContextPreviewResponse withToast(ContextPreviewResponse preview) {
        Locale locale = LocaleContextHolder.getLocale();
        long successCount = preview.sources().stream().filter(s -> s.status() == SourceStatus.SUCCESS).count();
        long failedCount = preview.sources().stream().filter(s -> s.status() == SourceStatus.FAILED).count();

        ToastMessage toast;
        if (failedCount == 0) {
            String title = messageSource.getMessage("toast.context.success", null, locale);
            String desc = messageSource.getMessage("toast.context.success.desc", new Object[]{successCount}, locale);
            toast = new ToastMessage(title, desc);
        } else {
            String title = messageSource.getMessage("toast.context.partial", null, locale);
            String desc = messageSource.getMessage("toast.context.partial.desc", new Object[]{successCount, failedCount}, locale);
            toast = new ToastMessage(title, desc);
        }

        return new ContextPreviewResponse(preview.sources(), toast);
    }
}
