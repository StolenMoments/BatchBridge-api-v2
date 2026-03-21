package org.jh.batchbridge.config;

import java.util.List;
import java.util.Map;
import org.jh.batchbridge.dto.response.ModelResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "batch-bridge")
public class ModelListProperties {
    private Map<String, List<ModelResponse>> supportedModels;

    public Map<String, List<ModelResponse>> getSupportedModels() {
        return supportedModels;
    }

    public void setSupportedModels(Map<String, List<ModelResponse>> supportedModels) {
        this.supportedModels = supportedModels;
    }
}
