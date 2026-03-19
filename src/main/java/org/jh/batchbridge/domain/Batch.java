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

    public boolean isEditable() {
        return this.status == BatchStatus.DRAFT;
    }

    public void submit(String externalBatchId) {
        if (this.status != BatchStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT batches can be submitted.");
        }
        this.status = BatchStatus.IN_PROGRESS;
        this.externalBatchId = externalBatchId;
        this.submittedAt = LocalDateTime.now();
    }

    public void complete(java.util.Map<Long, PromptResult> results) {
        if (this.status != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Batch must be IN_PROGRESS to complete.");
        }
        this.prompts.forEach(p -> {
            PromptResult r = results.get(p.getId());
            if (r != null && r.success()) {
                p.complete(r.responseContent());
            } else {
                p.fail(r != null ? r.errorMessage() : "결과 없음");
            }
        });
        this.status = BatchStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void fail(String errorMessage) {
        this.status = BatchStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
        for (BatchPrompt prompt : prompts) {
            prompt.fail(errorMessage);
        }
    }

    // Legacy method support (can be removed if Service is updated)
    public void markInProgress() {
         // This is now redundant or can call submit(null) if externalBatchId is optional,
         // but submit requires string.
         // We will leave this for now or remove it if we update Service.
         if (this.status != BatchStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT batches can be moved to IN_PROGRESS.");
        }
        this.status = BatchStatus.IN_PROGRESS;
        this.submittedAt = LocalDateTime.now();
    }

    // Legacy method support
    public void complete(String result) {
        if (this.status != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Batch must be IN_PROGRESS to complete.");
        }
        this.errorMessage = null;
        this.status = BatchStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        if (!prompts.isEmpty()) {
            prompts.get(0).complete(result);
        }
    }

    public void failOnSubmission(String errorMessage) {
        fail(errorMessage);
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
