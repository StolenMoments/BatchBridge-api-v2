package org.jh.batchbridge.exception;

public class ReferencePromptNotCompletedException extends RuntimeException {
    public ReferencePromptNotCompletedException(Long promptId) {
        super("Referenced prompt " + promptId + " has no completed media.");
    }
}
