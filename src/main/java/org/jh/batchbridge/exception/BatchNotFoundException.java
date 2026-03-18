package org.jh.batchbridge.exception;

public class BatchNotFoundException extends RuntimeException {

    public BatchNotFoundException(Long batchId) {
        super("Batch not found: " + batchId);
    }

    public BatchNotFoundException(String message) {
        super(message);
    }
}
