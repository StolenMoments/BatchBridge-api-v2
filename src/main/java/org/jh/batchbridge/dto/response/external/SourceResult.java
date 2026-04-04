package org.jh.batchbridge.dto.response.external;

import org.springframework.lang.Nullable;

public record SourceResult(
        SourceType type,
        String id,
        @Nullable String title,
        SourceStatus status,
        @Nullable String error
) {
}
