package org.jh.batchbridge.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.ExternalBatchStatus;
import org.jh.batchbridge.dto.response.ModelInfo;
import org.jh.batchbridge.exception.UnsupportedModelException;
import org.junit.jupiter.api.Test;

class BatchApiClientFactoryTest {

    @Test
    void returnsMatchingAdapterByModelPrefix() {
        BatchApiPort claudeAdapter = new StubAdapter("claude");
        BatchApiPort grokAdapter = new StubAdapter("grok");
        BatchApiClientFactory factory = new BatchApiClientFactory(List.of(claudeAdapter, grokAdapter));

        BatchApiPort adapter = factory.getAdapter("Claude-3-7-sonnet");

        assertThat(adapter).isSameAs(claudeAdapter);
    }

    @Test
    void throwsUnsupportedModelExceptionForUnknownModel() {
        BatchApiClientFactory factory = new BatchApiClientFactory(List.of(new StubAdapter("claude")));

        assertThatThrownBy(() -> factory.getAdapter("gemini-2.0-flash"))
                .isInstanceOf(UnsupportedModelException.class)
                .hasMessageContaining("gemini-2.0-flash");
    }

    @Test
    void throwsUnsupportedModelExceptionForNullModel() {
        BatchApiClientFactory factory = new BatchApiClientFactory(List.of(new StubAdapter("claude")));

        assertThatThrownBy(() -> factory.getAdapter(null))
                .isInstanceOf(UnsupportedModelException.class);
    }

    @Test
    void throwsUnsupportedModelExceptionForBlankModel() {
        BatchApiClientFactory factory = new BatchApiClientFactory(List.of(new StubAdapter("claude")));

        assertThatThrownBy(() -> factory.getAdapter("   "))
                .isInstanceOf(UnsupportedModelException.class);
    }

    @Test
    void normalizesCaseWhenMatchingAdapter() {
        BatchApiPort claudeAdapter = new StubAdapter("claude-");
        BatchApiClientFactory factory = new BatchApiClientFactory(List.of(claudeAdapter));

        BatchApiPort result = factory.getAdapter("CLAUDE-3-sonnet");

        assertThat(result).isSameAs(claudeAdapter);
    }

    @Test
    void throwsUnsupportedModelWhenAdapterListIsEmpty() {
        BatchApiClientFactory factory = new BatchApiClientFactory(List.of());

        assertThatThrownBy(() -> factory.getAdapter("claude-3"))
                .isInstanceOf(UnsupportedModelException.class);
    }

    @Test
    void routesGrokModelToXaiAdapter() {
        BatchApiPort claudeAdapter = new StubAdapter("claude-");
        BatchApiPort xaiAdapter = new StubAdapter("grok-");
        BatchApiClientFactory factory = new BatchApiClientFactory(List.of(claudeAdapter, xaiAdapter));

        BatchApiPort adapter = factory.getAdapter("grok-3");

        assertThat(adapter).isSameAs(xaiAdapter);
    }

    private record StubAdapter(String prefix) implements BatchApiPort {

        @Override
        public String getSupportedModelPrefix() {
            return prefix;
        }

        @Override
        public ExternalBatchId submitBatch(BatchSubmitRequest request) {
            return new ExternalBatchId("external-id");
        }

        @Override
        public BatchStatusResult fetchStatus(ExternalBatchId externalBatchId) {
            return new BatchStatusResult(ExternalBatchStatus.IN_PROGRESS, null);
        }

        @Override
        public Map<Long, PromptResult> fetchResults(ExternalBatchId externalBatchId, List<BatchPrompt> prompts) {
            return Map.of();
        }

        @Override
        public List<ModelInfo> fetchSupportedModels() {
            return List.of();
        }
    }
}
