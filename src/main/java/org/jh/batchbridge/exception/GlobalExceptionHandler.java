package org.jh.batchbridge.exception;

import org.jh.batchbridge.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BatchNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBatchNotFound(BatchNotFoundException e) {
        return buildError(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(BatchNotEditableException.class)
    public ResponseEntity<ApiResponse<Void>> handleBatchNotEditable(BatchNotEditableException e) {
        return buildError(HttpStatus.CONFLICT, "BATCH_NOT_EDITABLE", e.getMessage());
    }

    @ExceptionHandler(BatchEmptyException.class)
    public ResponseEntity<ApiResponse<Void>> handleBatchEmpty(BatchEmptyException e) {
        return buildError(HttpStatus.BAD_REQUEST, "BATCH_EMPTY", e.getMessage());
    }

    @ExceptionHandler(PromptNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePromptNotFound(PromptNotFoundException e) {
        return buildError(HttpStatus.NOT_FOUND, "PROMPT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(BatchResultNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBatchResultNotFound(BatchResultNotFoundException e) {
        return buildError(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(UnsupportedModelException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedModel(UnsupportedModelException e) {
        return buildError(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MODEL", e.getMessage());
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalApi(ExternalApiException e) {
        return buildError(HttpStatus.BAD_GATEWAY, "EXTERNAL_API_ERROR", e.getMessage());
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(org.springframework.web.bind.MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining(", "));
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = e.getName() + ": invalid value '" + e.getValue() + "'";
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandled(Exception e) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Internal server error");
    }

    private ResponseEntity<ApiResponse<Void>> buildError(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(code, message));
    }
}
