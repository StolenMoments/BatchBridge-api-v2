package org.jh.batchbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.config.ModelListProperties;
import org.jh.batchbridge.dto.response.ModelInfo;
import org.jh.batchbridge.dto.response.ModelResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelServiceTest {

    @Mock
    private BatchApiPort adapter;

    @Mock
    private ModelListProperties modelListProperties;

    @InjectMocks
    private ModelService modelService;

    @Spy
    private java.util.ArrayList<BatchApiPort> adapters;

    @Test
    void getAllModels_CombinesDynamicAndStaticModels() {
        // given
        adapters.add(adapter);
        when(adapter.fetchSupportedModels()).thenReturn(List.of(new ModelInfo("claude-1", "Claude 1")));
        
        ModelResponse staticModel = new ModelResponse("grok-1", "Grok 1");
        when(modelListProperties.getSupportedModels()).thenReturn(Map.of("grok", List.of(staticModel)));

        // when
        List<ModelInfo> result = modelService.getAllModels();

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ModelInfo::id).containsExactlyInAnyOrder("claude-1", "grok-1");
        assertThat(result).extracting(ModelInfo::displayName).containsExactlyInAnyOrder("Claude 1", "Grok 1");
    }

    @Test
    void getAllModels_HandlesAdapterFailure() {
        // given
        adapters.add(adapter);
        when(adapter.fetchSupportedModels()).thenThrow(new RuntimeException("API Failure"));
        when(modelListProperties.getSupportedModels()).thenReturn(null);

        // when
        List<ModelInfo> result = modelService.getAllModels();

        // then
        assertThat(result).isEmpty();
    }
}
