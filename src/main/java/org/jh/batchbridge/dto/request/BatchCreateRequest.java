package org.jh.batchbridge.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BatchCreateRequest(
        String label,
        @NotBlank String model,
        String systemPrompt,
        @NotBlank String userPrompt
) {
}
