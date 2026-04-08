package org.jh.batchbridge.dto.request;

import org.springframework.lang.Nullable;

public record BatchUpdateRequest(
        @Nullable String label,
        @Nullable String model
) {
}
