package org.jh.batchbridge.dto.response;

import java.time.LocalDateTime;
import org.jh.batchbridge.domain.PromptTemplate;

public record PromptTemplateResponse(
        Long id,
        String name,
        String description,
        String systemPrompt,
        String userPrompt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PromptTemplateResponse from(PromptTemplate template) {
        return new PromptTemplateResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getSystemPrompt(),
                template.getUserPrompt(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
