package org.jh.batchbridge.dto.external;

import java.util.List;
import org.jh.batchbridge.domain.Batch;
import org.springframework.lang.Nullable;

public record BatchSubmitRequest(
        String label,
        String model,
        List<PromptItem> prompts
) {
    public record PromptItem(
            Long promptId,
            @Nullable String systemPrompt,
            String userPrompt,
            List<AttachmentItem> attachments
    ) {
    }

    public record AttachmentItem(
            String fileName,
            String fileContent
    ) {
    }

    public static BatchSubmitRequest from(Batch batch) {
        List<PromptItem> items = batch.getPrompts().stream()
                .map(prompt -> new PromptItem(
                        prompt.getId(),
                        prompt.getSystemPrompt(),
                        prompt.getUserPrompt(),
                        prompt.getAttachments().stream()
                                .map(attachment -> new AttachmentItem(attachment.getFileName(), attachment.getFileContent()))
                                .toList()
                ))
                .toList();
        return new BatchSubmitRequest(batch.getLabel(), batch.getModel(), items);
    }
}
