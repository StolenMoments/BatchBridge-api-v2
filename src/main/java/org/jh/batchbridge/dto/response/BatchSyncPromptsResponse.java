package org.jh.batchbridge.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response for batch prompt resynchronization")
public record BatchSyncPromptsResponse(
        @Schema(description = "Batch ID")
        Long id,
        @Schema(description = "Number of resynced prompts")
        int resynced,
        @Schema(description = "Number of prompts still failed")
        int stillFailed
) {
}
