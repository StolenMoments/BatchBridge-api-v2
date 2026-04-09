package org.jh.batchbridge.exception;

public class PromptTemplateNotFoundException extends RuntimeException {

    public PromptTemplateNotFoundException(Long id) {
        super("PromptTemplate not found: " + id);
    }
}
