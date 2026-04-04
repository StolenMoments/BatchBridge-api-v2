package org.jh.batchbridge.dto.response.external;

import java.util.List;

public record ContextPreviewResponse(
        List<SourceResult> sources
) {
}
