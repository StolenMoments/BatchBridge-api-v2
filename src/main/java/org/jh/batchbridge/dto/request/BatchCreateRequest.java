package org.jh.batchbridge.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BatchCreateRequest(
        @NotBlank String label,
        @NotBlank String model
) {
}
