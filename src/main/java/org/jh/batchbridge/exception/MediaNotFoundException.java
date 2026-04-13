package org.jh.batchbridge.exception;

public class MediaNotFoundException extends RuntimeException {

    public MediaNotFoundException(Long batchId, Long promptId) {
        super(String.format("Media not found for batch %d, prompt %d", batchId, promptId));
    }
}
