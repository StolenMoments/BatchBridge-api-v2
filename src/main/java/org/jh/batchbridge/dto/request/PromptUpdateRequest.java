package org.jh.batchbridge.dto.request;

import java.util.List;
import org.jh.batchbridge.domain.PromptType;
import org.springframework.lang.Nullable;

public record PromptUpdateRequest(
        @Nullable String label,
        @Nullable String systemPrompt,
        @Nullable String userPrompt,
        @Nullable PromptType promptType,
        @Nullable String referenceMediaUrl,
        @Nullable Long referencePromptId,
        @Nullable List<PromptAttachmentRequest> attachments
) {
}
