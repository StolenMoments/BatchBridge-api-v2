package org.jh.batchbridge.exception;

public class BatchNotSyncedException extends RuntimeException {
    public BatchNotSyncedException(String message) {
        super(message);
    }
}
