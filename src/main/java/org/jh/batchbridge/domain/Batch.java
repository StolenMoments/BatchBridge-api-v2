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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity
@Table(name = "batch")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Batch {

    private static final Logger log = LoggerFactory.getLogger(Batch.class);
    private static final String DEFAULT_PROMPT_ERROR_MESSAGE = "No result found for prompt";
    private static final String PROMPT_PROCESSING_ERROR_MESSAGE = "Failed to process prompt result";

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

    public static Batch createDraft(String label, String model) {
        return Batch.builder()
                .label(label)
                .model(model)
                .status(BatchStatus.DRAFT)
                .prompts(new ArrayList<>())
                .build();
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

    public void complete(Map<Long, PromptResult> results) {
        if (this.status != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Batch must be IN_PROGRESS to complete.");
        }

        Map<Long, PromptResult> safeResults = results == null ? Map.of() : results;
        this.prompts.forEach(prompt -> applyPromptResult(prompt, safeResults));
        this.status = BatchStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    private void applyPromptResult(BatchPrompt prompt, Map<Long, PromptResult> results) {
        try {
            PromptResult result = results.get(prompt.getId());
            if (result != null && result.success()) {
                prompt.complete(result.responseContent());
                return;
            }

            String errorMessage = DEFAULT_PROMPT_ERROR_MESSAGE;
            if (result != null && result.errorMessage() != null && !result.errorMessage().isBlank()) {
                errorMessage = result.errorMessage();
            }
            prompt.fail(errorMessage);
        } catch (RuntimeException e) {
            log.error("Failed to apply prompt result [batchId={}, promptId={}]", id, prompt.getId(), e);
            prompt.fail(PROMPT_PROCESSING_ERROR_MESSAGE);
        }
    }

    public void fail(String errorMessage) {
        if (this.status == BatchStatus.COMPLETED || this.status == BatchStatus.FAILED) {
            return;
        }
        this.status = BatchStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
        for (BatchPrompt prompt : prompts) {
            prompt.fail(errorMessage);
        }
    }
}
