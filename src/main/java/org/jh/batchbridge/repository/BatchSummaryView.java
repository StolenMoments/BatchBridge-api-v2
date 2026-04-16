package org.jh.batchbridge.repository;

import java.time.LocalDateTime;
import org.jh.batchbridge.domain.BatchStatus;

public interface BatchSummaryView {
    Long getId();
    String getLabel();
    String getModel();
    BatchStatus getStatus();
    long getPromptCount();
    long getSuccessCount();
    long getFailedCount();
    LocalDateTime getCreatedAt();
    LocalDateTime getSubmittedAt();
    LocalDateTime getCompletedAt();
}
