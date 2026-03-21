package org.jh.batchbridge.service;

import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.config.ModelListProperties;
import org.jh.batchbridge.dto.response.ModelInfo;
import org.jh.batchbridge.dto.response.ModelResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelService {
    private final List<BatchApiPort> adapters;
    private final ModelListProperties modelListProperties;

    public List<ModelInfo> getAllModels() {
        Stream<ModelInfo> dynamicModels = adapters.stream()
                .flatMap(adapter -> {
                    try {
                        return adapter.fetchSupportedModels().stream();
                    } catch (Exception e) {
                        log.warn("모델 목록 조회 실패 [{}]: {}",
                                adapter.getSupportedModelPrefix(), e.getMessage());
                        return Stream.empty();
                    }
                });

        Stream<ModelInfo> staticModels = Stream.empty();
        if (modelListProperties.getSupportedModels() != null) {
            staticModels = modelListProperties.getSupportedModels().values().stream()
                    .flatMap(List::stream)
                    .map(model -> new ModelInfo(model.id(), model.displayName()));
        }

        return Stream.concat(dynamicModels, staticModels).toList();
    }
}
