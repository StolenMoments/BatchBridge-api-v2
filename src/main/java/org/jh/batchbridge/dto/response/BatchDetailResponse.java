package org.jh.batchbridge.dto.response;

import java.time.LocalDateTime;
import org.jh.batchbridge.domain.BatchStatus;
import org.springframework.lang.Nullable;

public record BatchDetailResponse(
        Long id,
        String label,
        String model,
        BatchStatus status,
        String systemPrompt,
        String userPrompt,
        @Nullable String responseContent,
        @Nullable String errorMessage,
        LocalDateTime createdAt,
        @Nullable LocalDateTime completedAt
) {
}
