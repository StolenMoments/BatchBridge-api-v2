package org.jh.batchbridge.dto.response;

import java.time.LocalDateTime;

public record PromptAttachmentResponse(
        Long id,
        String fileName,
        String fileContent,
        LocalDateTime createdAt
) {
}
