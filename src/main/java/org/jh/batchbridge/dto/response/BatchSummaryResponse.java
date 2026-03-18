package org.jh.batchbridge.dto.response;

import java.time.LocalDateTime;
import org.jh.batchbridge.domain.BatchStatus;

public record BatchSummaryResponse(
        Long id,
        String label,
        String model,
        BatchStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
