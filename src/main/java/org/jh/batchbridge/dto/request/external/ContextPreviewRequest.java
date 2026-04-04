package org.jh.batchbridge.dto.request.external;

import java.util.List;

public record ContextPreviewRequest(
        String githubPrUrl,
        List<String> jiraKeys,
        List<String> confluencePageIds
) {
}
