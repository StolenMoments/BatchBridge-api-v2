package org.jh.batchbridge.dto.response.external;

import org.springframework.lang.Nullable;

import java.util.List;

public record ContextPreviewResponse(
        String contextText,
        List<SourceResult> sources,
        @Nullable ToastMessage toast
) {
}
