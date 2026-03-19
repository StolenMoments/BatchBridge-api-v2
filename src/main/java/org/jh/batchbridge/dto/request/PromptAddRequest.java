package org.jh.batchbridge.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.springframework.lang.Nullable;

public record PromptAddRequest(
        @Nullable String label,
        @Nullable String systemPrompt,
        @NotBlank(message = "userPrompt is required") String userPrompt
) {
}
