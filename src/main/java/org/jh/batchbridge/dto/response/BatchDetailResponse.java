package org.jh.batchbridge.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import org.jh.batchbridge.domain.BatchStatus;
import org.springframework.lang.Nullable;

public record BatchDetailResponse(
        Long id,
        String label,
        String model,
        BatchStatus status,
        List<BatchPromptResponse> prompts,
        @Nullable String errorMessage,
        LocalDateTime createdAt,
        @Nullable LocalDateTime completedAt
) {
}
