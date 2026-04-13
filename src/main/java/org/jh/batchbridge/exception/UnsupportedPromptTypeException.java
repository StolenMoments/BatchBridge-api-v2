package org.jh.batchbridge.exception;

import org.jh.batchbridge.domain.PromptType;

public class UnsupportedPromptTypeException extends RuntimeException {

    public UnsupportedPromptTypeException(PromptType promptType, String modelPrefix) {
        super(String.format("Unsupported prompt type '%s' for adapter '%s'", promptType, modelPrefix));
    }
}
