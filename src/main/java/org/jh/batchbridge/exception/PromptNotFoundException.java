package org.jh.batchbridge.exception;

public class PromptNotFoundException extends RuntimeException {
    public PromptNotFoundException(Long id) {
        super("Prompt not found with id: " + id);
    }
}
