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
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "batch")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    @Builder.Default
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
    @Builder.Default
    private List<BatchPrompt> prompts = new ArrayList<>();

    public Batch(String label, String model) {
        this.label = label;
        this.model = model;
        this.status = BatchStatus.DRAFT;
        this.prompts = new ArrayList<>();
    }

    public void addPrompt(BatchPrompt prompt) {
        prompts.add(prompt);
        prompt.assignBatch(this);
    }

    public int getPromptCount() {
        return prompts.size();
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

    private static final String DEFAULT_PROMPT_ERROR_MESSAGE = "No result found for prompt";

    public void complete(Map<Long, PromptResult> results) {
        if (this.status != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Batch must be IN_PROGRESS to complete.");
        }
        this.prompts.forEach(p -> {
            PromptResult r = results.get(p.getId());
            if (r != null && r.success()) {
                p.complete(r.responseContent());
            } else {
                p.fail(r != null ? r.errorMessage() : DEFAULT_PROMPT_ERROR_MESSAGE);
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

    public void setExternalBatchId(String externalBatchId) {
        this.externalBatchId = externalBatchId;
    }
}
