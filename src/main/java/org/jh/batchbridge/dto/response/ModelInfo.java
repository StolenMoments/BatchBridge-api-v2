package org.jh.batchbridge.dto.response;

import java.util.List;
import org.jh.batchbridge.domain.PromptType;

public record ModelInfo(String id, String displayName, List<PromptType> supportedPromptTypes) {

    public ModelInfo(String id, String displayName) {
        this(id, displayName, List.of(PromptType.TEXT));
    }
}
