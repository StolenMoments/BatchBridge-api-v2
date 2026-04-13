package org.jh.batchbridge.dto.response;

import java.util.List;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptStatus;
import org.jh.batchbridge.domain.PromptType;

public record BatchPromptResponse(
        Long id,
        String label,
        String systemPrompt,
        String userPrompt,
        PromptStatus status,
        PromptType promptType,
        String referenceMediaUrl,
        String resultMediaPath,
        String responseContent,
        String errorMessage,
        List<PromptAttachmentResponse> attachments
) {
    public BatchPromptResponse(Long id, String label, String systemPrompt, String userPrompt, PromptStatus status, String responseContent, String errorMessage) {
        this(id, label, systemPrompt, userPrompt, status, PromptType.TEXT, null, null, responseContent, errorMessage, List.of());
    }

    public static BatchPromptResponse from(BatchPrompt prompt) {
        return new BatchPromptResponse(
                prompt.getId(),
                prompt.getLabel(),
                prompt.getSystemPrompt(),
                prompt.getUserPrompt(),
                prompt.getStatus(),
                prompt.getPromptType(),
                prompt.getReferenceMediaUrl(),
                prompt.getResultMediaPath(),
                prompt.getResponseContent(),
                prompt.getErrorMessage(),
                prompt.getAttachments().stream()
                        .map(attachment -> new PromptAttachmentResponse(
                                attachment.getId(),
                                attachment.getFileName(),
                                attachment.getFileContent(),
                                attachment.getCreatedAt()
                        ))
                        .toList()
        );
    }
}

