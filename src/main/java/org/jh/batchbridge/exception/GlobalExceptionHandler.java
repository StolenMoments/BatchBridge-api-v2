package org.jh.batchbridge.exception;

import org.jh.batchbridge.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BatchNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBatchNotFound(BatchNotFoundException e) {
        return buildError(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(BatchNotEditableException.class)
    public ResponseEntity<ApiResponse<Void>> handleBatchNotEditable(BatchNotEditableException e) {
        return buildError(HttpStatus.CONFLICT, "BATCH_NOT_EDITABLE", e.getMessage());
    }

    @ExceptionHandler(BatchNotSyncedException.class)
    public ResponseEntity<ApiResponse<Void>> handleBatchNotSynced(BatchNotSyncedException e) {
        return buildError(HttpStatus.CONFLICT, "BATCH_NOT_SYNCED", e.getMessage());
    }

    @ExceptionHandler(BatchEmptyException.class)
    public ResponseEntity<ApiResponse<Void>> handleBatchEmpty(BatchEmptyException e) {
        return buildError(HttpStatus.BAD_REQUEST, "BATCH_EMPTY", e.getMessage());
    }

    @ExceptionHandler(PromptNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePromptNotFound(PromptNotFoundException e) {
        return buildError(HttpStatus.NOT_FOUND, "PROMPT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(PromptTemplateNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePromptTemplateNotFound(PromptTemplateNotFoundException e) {
        return buildError(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(BatchResultNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBatchResultNotFound(BatchResultNotFoundException e) {
        return buildError(HttpStatus.NOT_FOUND, "BATCH_RESULT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(UnsupportedModelException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedModel(UnsupportedModelException e) {
        return buildError(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MODEL", e.getMessage());
    }

    @ExceptionHandler(UnsupportedPromptTypeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedPromptType(UnsupportedPromptTypeException e) {
        return buildError(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROMPT_TYPE", e.getMessage());
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalApi(ExternalApiException e) {
        String message = messageSource.getMessage("toast.context.failed.desc", null, LocaleContextHolder.getLocale());
        return buildError(HttpStatus.BAD_GATEWAY, "CONTEXT_FETCH_FAILED", message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = e.getName() + ": invalid value '" + e.getValue() + "'";
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandled(Exception e) {
        log.error("Unhandled exception", e);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Internal server error");
    }

    private ResponseEntity<ApiResponse<Void>> buildError(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(code, message));
    }
}
