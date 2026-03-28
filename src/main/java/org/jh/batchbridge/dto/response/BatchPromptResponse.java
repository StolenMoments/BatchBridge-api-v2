package org.jh.batchbridge.dto.response;

import java.util.List;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptStatus;

public record BatchPromptResponse(
        Long id,
        String label,
        String systemPrompt,
        String userPrompt,
        PromptStatus status,
        String responseContent,
        String errorMessage,
        List<PromptAttachmentResponse> attachments
) {
    public BatchPromptResponse(Long id, String label, String systemPrompt, String userPrompt, PromptStatus status, String responseContent, String errorMessage) {
        this(id, label, systemPrompt, userPrompt, status, responseContent, errorMessage, List.of());
    }

    public static BatchPromptResponse from(BatchPrompt prompt) {
        return new BatchPromptResponse(
                prompt.getId(),
                prompt.getLabel(),
                prompt.getSystemPrompt(),
                prompt.getUserPrompt(),
                prompt.getStatus(),
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

