package org.jh.batchbridge.dto.external;

import org.springframework.lang.Nullable;

public record BatchSubmitRequest(
        String internalRequestId,
        String model,
        @Nullable String systemPrompt,
        String userPrompt
) {
}
