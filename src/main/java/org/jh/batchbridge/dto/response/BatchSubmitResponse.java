package org.jh.batchbridge.dto.response;

import java.time.LocalDateTime;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;

public record BatchSubmitResponse(
        Long id,
        BatchStatus status,
        String externalBatchId,
        LocalDateTime submittedAt
) {
    public static BatchSubmitResponse from(Batch batch) {
        return new BatchSubmitResponse(
                batch.getId(),
                batch.getStatus(),
                batch.getExternalBatchId(),
                batch.getSubmittedAt()
        );
    }
}
