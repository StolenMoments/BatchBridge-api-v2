package org.jh.batchbridge.dto.external.xai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record XAiBatchResultsResponse(
        List<XAiBatchResultItem> results,
        @JsonProperty("pagination_token") String paginationToken
) {
    public record XAiBatchResultItem(
            @JsonProperty("batch_request_id") String batchRequestId,
            @JsonProperty("batch_result") XAiBatchResultContent batchResult,
            @JsonProperty("error_message") String errorMessage
    ) {}

    public record XAiBatchResultContent(
            XAiBatchResponseContent response
    ) {}

    public record XAiBatchResponseContent(
            @JsonProperty("chat_get_completion") XAiChatCompletion chatGetCompletion
    ) {}

    public record XAiChatCompletion(
            List<XAiChoice> choices
    ) {}

    public record XAiChoice(
            XAiMessage message
    ) {}

    public record XAiMessage(
            String content
    ) {}
}
