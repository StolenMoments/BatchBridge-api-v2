package org.jh.batchbridge.dto.response;

import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptStatus;
import org.springframework.lang.Nullable;

public record BatchPromptResponse(
        Long id,
        String label,
        @Nullable String systemPrompt,
        String userPrompt,
        PromptStatus status,
        @Nullable String responseContent,
        @Nullable String errorMessage
) {
    public static BatchPromptResponse from(BatchPrompt prompt) {
        return new BatchPromptResponse(
                prompt.getId(),
                prompt.getLabel(),
                prompt.getSystemPrompt(),
                prompt.getUserPrompt(),
                prompt.getStatus(),
                prompt.getResponseContent(),
                prompt.getErrorMessage()
        );
    }
}
