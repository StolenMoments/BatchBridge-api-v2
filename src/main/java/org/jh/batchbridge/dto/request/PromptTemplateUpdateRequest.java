package org.jh.batchbridge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record PromptTemplateUpdateRequest(
        @Size(max = 100, message = "name must be at most 100 characters")
        @NotBlank(message = "name is required") String name,
        @Size(max = 300, message = "description must be at most 300 characters")
        @Nullable String description,
        @Nullable String systemPrompt,
        @NotBlank(message = "userPrompt is required") String userPrompt
) {
}
