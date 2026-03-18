package org.jh.batchbridge.exception;

public class UnsupportedModelException extends RuntimeException {

    public UnsupportedModelException(String model) {
        super("Unsupported model: " + model);
    }
}
