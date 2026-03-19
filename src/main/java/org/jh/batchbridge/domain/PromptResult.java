package org.jh.batchbridge.domain;

import org.springframework.lang.Nullable;

public record PromptResult(
    boolean success,
    @Nullable String responseContent,
    @Nullable String errorMessage
) {
}
