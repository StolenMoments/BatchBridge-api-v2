package org.jh.batchbridge.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "batch")
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 50)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status = BatchStatus.DRAFT;

    @Column(length = 100)
    private String externalBatchId;

    @Column(length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BatchPrompt> prompts = new ArrayList<>();

    protected Batch() {
    }

    public Batch(String label, String model) {
        this.label = label;
        this.model = model;
        this.status = BatchStatus.DRAFT;
    }

    public void addPrompt(BatchPrompt prompt) {
        prompts.add(prompt);
        prompt.assignBatch(this);
    }

    public BatchPrompt getFirstPrompt() {
        return prompts.isEmpty() ? null : prompts.get(0);
    }

    public void markInProgress() {
        if (this.status != BatchStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT batches can be moved to IN_PROGRESS.");
        }
        this.status = BatchStatus.IN_PROGRESS;
        this.submittedAt = LocalDateTime.now();
    }

    public void complete(String result) {
        validateTransitionFromInProgress();
        this.errorMessage = null;
        this.status = BatchStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        if (!prompts.isEmpty()) {
            prompts.get(0).complete(result);
        }
    }

    public void fail(String errorMessage) {
        validateTransitionFromInProgress();
        this.errorMessage = errorMessage;
        this.status = BatchStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        for (BatchPrompt prompt : prompts) {
            prompt.fail(errorMessage);
        }
    }

    public void failOnSubmission(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = BatchStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        for (BatchPrompt prompt : prompts) {
            prompt.fail(errorMessage);
        }
    }

    private void validateTransitionFromInProgress() {
        if (this.status != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Batch must be IN_PROGRESS to complete or fail.");
        }
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

    public BatchStatus getStatus() {
        return status;
    }

    public String getExternalBatchId() {
        return externalBatchId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public List<BatchPrompt> getPrompts() {
        return prompts;
    }

    public void setExternalBatchId(String externalBatchId) {
        this.externalBatchId = externalBatchId;
    }
}
