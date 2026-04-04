package org.jh.batchbridge.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.lang.Nullable;

public record BatchCreateRequest(
        @Nullable String label,
        @NotBlank String model,
        @NotNull @Valid PromptPayload prompt
) {
    public record PromptPayload(
            @Nullable String label,
            @Nullable String systemPrompt,
            @NotBlank(message = "userPrompt is required") String userPrompt,
            @Nullable String contextText,
            @Nullable List<PromptAttachmentRequest> attachments
    ) {
        public PromptPayload(String label, String systemPrompt, String userPrompt) {
            this(label, systemPrompt, userPrompt, null, null);
        }
    }
}
