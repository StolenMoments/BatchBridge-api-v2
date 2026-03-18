package org.jh.batchbridge.dto.external.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClaudeBatchResponse(
        String id,
        String type,
        @JsonProperty("processing_status") String processingStatus,
        ClaudeError error
) {
    public record ClaudeError(
            String type,
            String message
    ) {}
}
