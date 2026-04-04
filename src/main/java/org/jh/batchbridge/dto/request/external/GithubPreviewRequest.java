package org.jh.batchbridge.dto.request.external;

import jakarta.validation.constraints.NotBlank;

public record GithubPreviewRequest(
        @NotBlank(message = "owner is required") String owner,
        @NotBlank(message = "repo is required") String repo,
        @NotBlank(message = "path is required") String path,
        String ref
) {
}
