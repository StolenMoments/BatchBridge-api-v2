package org.jh.batchbridge.dto.request;

import org.springframework.lang.Nullable;

public record PromptUpdateRequest(
        @Nullable String label,
        @Nullable String systemPrompt,
        @Nullable String userPrompt
) {
}
