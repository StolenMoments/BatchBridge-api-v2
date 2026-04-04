package org.jh.batchbridge.dto.request;

import java.util.List;
import org.springframework.lang.Nullable;

public record PromptUpdateRequest(
        @Nullable String label,
        @Nullable String systemPrompt,
        @Nullable String userPrompt,
        @Nullable String contextText,
        @Nullable List<PromptAttachmentRequest> attachments
) {
}
