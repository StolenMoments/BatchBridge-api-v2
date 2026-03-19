package org.jh.batchbridge.exception;

public class BatchEmptyException extends RuntimeException {
    public BatchEmptyException(String message) {
        super(message);
    }
}
