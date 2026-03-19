package org.jh.batchbridge.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.springframework.lang.Nullable;

public record BatchDetailResponse(
        Long id,
        String label,
        String model,
        BatchStatus status,
        int promptCount,
        @Nullable String externalBatchId,
        @Nullable String errorMessage,
        LocalDateTime createdAt,
        @Nullable LocalDateTime submittedAt,
        @Nullable LocalDateTime completedAt,
        List<BatchPromptResponse> prompts
) {
    public static BatchDetailResponse from(Batch batch) {
        List<BatchPromptResponse> promptResponses = batch.getPrompts().stream()
                .map(BatchPromptResponse::from)
                .toList();

        return new BatchDetailResponse(
                batch.getId(),
                batch.getLabel(),
                batch.getModel(),
                batch.getStatus(),
                batch.getPromptCount(),
                batch.getExternalBatchId(),
                batch.getErrorMessage(),
                batch.getCreatedAt(),
                batch.getSubmittedAt(),
                batch.getCompletedAt(),
                promptResponses
        );
    }
}
