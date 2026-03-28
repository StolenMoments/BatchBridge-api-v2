package org.jh.batchbridge.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PromptAttachmentRequest(
        @NotBlank(message = "fileName is required") String fileName,
        @NotBlank(message = "fileContent is required") String fileContent
) {
}
