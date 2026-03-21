package org.jh.batchbridge.dto.external;

import org.springframework.lang.Nullable;

public record BatchStatusResult(
        ExternalBatchStatus status,
        @Nullable String errorMessage
) {
}
