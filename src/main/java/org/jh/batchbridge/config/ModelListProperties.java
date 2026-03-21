package org.jh.batchbridge.config;

import java.util.List;
import org.jh.batchbridge.dto.response.ModelResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "batch-bridge")
public class ModelListProperties {
    private List<ModelResponse> supportedModels;

    public List<ModelResponse> getSupportedModels() {
        return supportedModels;
    }

    public void setSupportedModels(List<ModelResponse> supportedModels) {
        this.supportedModels = supportedModels;
    }
}
