package org.jh.batchbridge.dto;

import org.springframework.lang.Nullable;

public record ApiResponse<T>(
        boolean success,
        @Nullable T data,
        @Nullable ErrorResponse error
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorResponse(code, message));
    }

    public record ErrorResponse(
            String code,
            String message
    ) {
    }
}
