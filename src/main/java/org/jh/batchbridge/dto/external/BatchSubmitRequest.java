package org.jh.batchbridge.dto.external;

import java.util.List;
import org.jh.batchbridge.domain.Batch;
import org.springframework.lang.Nullable;

public record BatchSubmitRequest(
        String model,
        List<PromptItem> prompts
) {
    public record PromptItem(
            Long promptId,
            @Nullable String systemPrompt,
            String userPrompt
    ) {
    }

    public static BatchSubmitRequest from(Batch batch) {
        List<PromptItem> items = batch.getPrompts().stream()
                .map(prompt -> new PromptItem(prompt.getId(), prompt.getSystemPrompt(), prompt.getUserPrompt()))
                .toList();
        return new BatchSubmitRequest(batch.getModel(), items);
    }
}
