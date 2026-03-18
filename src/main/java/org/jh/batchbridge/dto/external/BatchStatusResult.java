package org.jh.batchbridge.dto.external;

import org.springframework.lang.Nullable;

public record BatchStatusResult(
        BatchStatus status,
        @Nullable String errorMessage
) {
}
