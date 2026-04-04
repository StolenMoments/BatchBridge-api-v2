package org.jh.batchbridge.dto.response.external;

import org.springframework.lang.Nullable;

public record ExternalContextPreviewResponse(
        String label,
        String content,
        @Nullable ToastMessage toast
) {
}
