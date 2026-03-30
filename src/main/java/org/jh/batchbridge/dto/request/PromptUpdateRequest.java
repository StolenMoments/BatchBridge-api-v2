package org.jh.batchbridge.dto.request;

import java.util.List;
import org.springframework.lang.Nullable;

public record PromptUpdateRequest(
        @Nullable String label,
        @Nullable String systemPrompt,
        @Nullable String userPrompt,
        @Nullable List<PromptAttachmentRequest> attachments
) {
    public PromptUpdateRequest(String label, String systemPrompt, String userPrompt) {
        this(label, systemPrompt, userPrompt, null);
    }
}
