package org.jh.batchbridge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "batch_request")
public class BatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String label;

    @Column(length = 50, nullable = false)
    private String model;

    @Lob
    @Column(nullable = false)
    private String systemPrompt;

    @Lob
    @Column(nullable = false)
    private String userPrompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status = BatchStatus.PENDING;

    @Column(length = 100)
    private String externalBatchId;

    @Lob
    private String responseContent;

    @Column(length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    protected BatchRequest() {
    }

    public BatchRequest(String label, String model, String systemPrompt, String userPrompt) {
        this.label = label;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.status = BatchStatus.PENDING;
    }

    public void complete(String content) {
        validateTransitionFromInProgress();
        this.responseContent = content;
        this.errorMessage = null;
        this.status = BatchStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        validateTransitionFromInProgress();
        this.responseContent = null;
        this.errorMessage = errorMessage;
        this.status = BatchStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    private void validateTransitionFromInProgress() {
        if (this.status != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Batch request must be IN_PROGRESS to complete or fail.");
        }
    }

    public void markInProgress() {
        if (this.status != BatchStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be moved to IN_PROGRESS.");
        }
        this.status = BatchStatus.IN_PROGRESS;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getModel() {
        return model;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public String getExternalBatchId() {
        return externalBatchId;
    }

    public String getResponseContent() {
        return responseContent;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setExternalBatchId(String externalBatchId) {
        this.externalBatchId = externalBatchId;
    }
}
