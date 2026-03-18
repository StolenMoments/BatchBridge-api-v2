package org.jh.batchbridge.exception;

public class BatchResultNotFoundException extends RuntimeException {

    public BatchResultNotFoundException(Long batchId) {
        super("Batch result not found: " + batchId);
    }

    public BatchResultNotFoundException(String message) {
        super(message);
    }
}
