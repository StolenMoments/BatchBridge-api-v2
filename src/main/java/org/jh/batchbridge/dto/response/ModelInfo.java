package org.jh.batchbridge.dto.response;

import java.util.List;
import org.jh.batchbridge.domain.PromptType;
import org.springframework.lang.Nullable;

public record ModelInfo(String id, String displayName, @Nullable List<PromptType> supportedPromptTypes) {

    public ModelInfo(String id, String displayName) {
        this(id, displayName, null);
    }
}
