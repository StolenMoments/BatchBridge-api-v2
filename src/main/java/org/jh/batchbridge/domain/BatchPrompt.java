package org.jh.batchbridge.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "batch_prompt")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BatchPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(nullable = false, length = 100)
    private String label;

    @JdbcTypeCode(Types.LONGVARCHAR)
    private String systemPrompt;

    @Column(nullable = false)
    @JdbcTypeCode(Types.LONGVARCHAR)
    private String userPrompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PromptStatus status = PromptStatus.PENDING;

    @Lob
    private String responseContent;

    @Column(length = 500)
    private String errorMessage;

    @OneToMany(mappedBy = "prompt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PromptAttachment> attachments = new ArrayList<>();

    public static BatchPrompt create(String label, String systemPrompt, String userPrompt) {
        return create(label, systemPrompt, userPrompt, null);
    }

    public static BatchPrompt create(String label, String systemPrompt, String userPrompt, List<PromptAttachment> attachments) {
        BatchPrompt prompt = BatchPrompt.builder()
                .label(label)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .status(PromptStatus.PENDING)
                .build();
        if (attachments != null) {
            attachments.forEach(prompt::addAttachment);
        }
        return prompt;
    }

    public void addAttachment(PromptAttachment attachment) {
        attachments.add(attachment);
        attachment.assignPrompt(this);
    }

    public void assignBatch(Batch batch) {
        this.batch = batch;
    }

    public void update(String label, String systemPrompt, String userPrompt, List<PromptAttachment> attachments) {
        if (batch != null && !batch.isEditable()) {
            throw new IllegalStateException("Cannot update prompt of a non-draft batch.");
        }
        this.label = label;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        if (attachments != null) {
            this.attachments.clear();
            attachments.forEach(this::addAttachment);
        }
    }

    public void clearAttachments() {
        this.attachments.clear();
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
