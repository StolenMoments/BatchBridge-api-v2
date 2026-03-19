package org.jh.batchbridge.dto.response;

import java.time.LocalDateTime;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.repository.BatchSummaryView;
import org.springframework.lang.Nullable;

public record BatchSummaryResponse(
        Long id,
        String label,
        String model,
        BatchStatus status,
        int promptCount,
        LocalDateTime createdAt,
        @Nullable LocalDateTime submittedAt,
        @Nullable LocalDateTime completedAt
) {
    public static BatchSummaryResponse from(Batch batch) {
        return new BatchSummaryResponse(
                batch.getId(),
                batch.getLabel(),
                batch.getModel(),
                batch.getStatus(),
                batch.getPromptCount(),
                batch.getCreatedAt(),
                batch.getSubmittedAt(),
                batch.getCompletedAt()
        );
    }

    public static BatchSummaryResponse from(BatchSummaryView batch) {
        return new BatchSummaryResponse(
                batch.getId(),
                batch.getLabel(),
                batch.getModel(),
                batch.getStatus(),
                Math.toIntExact(batch.getPromptCount()),
                batch.getCreatedAt(),
                batch.getSubmittedAt(),
                batch.getCompletedAt()
        );
    }
}
