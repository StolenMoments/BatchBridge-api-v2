package org.jh.batchbridge.exception;

import org.jh.batchbridge.domain.PromptType;

public class UnsupportedPromptTypeException extends RuntimeException {

    public UnsupportedPromptTypeException(PromptType promptType) {
        super("Unsupported prompt type: " + promptType);
    }
}
