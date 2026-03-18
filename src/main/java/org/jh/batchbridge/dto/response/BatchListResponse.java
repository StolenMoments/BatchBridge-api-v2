package org.jh.batchbridge.dto.response;

import java.util.List;

public record BatchListResponse(
        List<BatchSummaryResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
