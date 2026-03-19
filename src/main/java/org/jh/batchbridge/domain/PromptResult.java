package org.jh.batchbridge.domain;

public record PromptResult(boolean success, String responseContent, String errorMessage) {
}
