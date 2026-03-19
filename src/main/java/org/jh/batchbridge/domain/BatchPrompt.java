package org.jh.batchbridge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "batch_prompt")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(nullable = false, length = 100)
    private String label;

    @Lob
    private String systemPrompt;

    @Lob
    @Column(nullable = false)
    private String userPrompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PromptStatus status = PromptStatus.PENDING;

    @Lob
    private String responseContent;

    @Column(length = 500)
    private String errorMessage;

    public BatchPrompt(String label, String systemPrompt, String userPrompt) {
        this.label = label;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.status = PromptStatus.PENDING;
    }

    public void assignBatch(Batch batch) {
        this.batch = batch;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }

    public void complete(String responseContent) {
        this.responseContent = responseContent;
        this.errorMessage = null;
        this.status = PromptStatus.COMPLETED;
    }

    public void fail(String errorMessage) {
        this.responseContent = null;
        this.errorMessage = errorMessage;
        this.status = PromptStatus.FAILED;
    }
}
