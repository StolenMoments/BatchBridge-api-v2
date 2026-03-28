package org.jh.batchbridge.dto.request;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import org.springframework.lang.Nullable;

public record PromptAddRequest(
        @Nullable String label,
        @Nullable String systemPrompt,
        @NotBlank(message = "userPrompt is required") String userPrompt,
        @Nullable List<PromptAttachmentRequest> attachments
) {
    public PromptAddRequest(String label, String systemPrompt, String userPrompt) {
        this(label, systemPrompt, userPrompt, null);
    }
}
