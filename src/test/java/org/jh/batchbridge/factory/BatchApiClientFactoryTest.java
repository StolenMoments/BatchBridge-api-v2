package org.jh.batchbridge.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.dto.external.BatchStatus;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
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
            return new BatchStatusResult(BatchStatus.IN_PROGRESS, null);
        }

        @Override
        public String fetchResult(ExternalBatchId externalBatchId) {
            return "";
        }
    }
}
