package org.jh.batchbridge.dto.request.external;

import jakarta.validation.constraints.NotBlank;

public record JiraPreviewRequest(
        @NotBlank(message = "issueKey is required") String issueKey
) {
}
