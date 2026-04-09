package org.jh.batchbridge.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.springframework.lang.Nullable;

public record PromptTemplateCreateRequest(
        @NotBlank(message = "name is required") String name,
        @Nullable String description,
        @Nullable String systemPrompt,
        @NotBlank(message = "userPrompt is required") String userPrompt
) {
}
