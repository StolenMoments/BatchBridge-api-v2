package org.jh.batchbridge.dto.external.xai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record XAiBatchResponse(
        @JsonProperty("batch_id") String batchId,
        XAiBatchState state
) {
    public record XAiBatchState(
            @JsonProperty("num_requests") int numRequests,
            @JsonProperty("num_pending") int numPending,
            @JsonProperty("num_success") int numSuccess,
            @JsonProperty("num_error") int numError
    ) {}
}
