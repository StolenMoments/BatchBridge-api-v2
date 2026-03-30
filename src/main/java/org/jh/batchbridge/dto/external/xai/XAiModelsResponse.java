package org.jh.batchbridge.dto.external.xai;

import java.util.List;

public record XAiModelsResponse(
        List<XAiModelData> models
) {
    public record XAiModelData(
            String id,
            String name
    ) {}
}
