package org.jh.batchbridge.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.factory.BatchApiClientFactory;
import org.jh.batchbridge.repository.BatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BatchStatusSyncWorkerTest {

    @Mock
    private BatchRepository repository;

    @Mock
    private BatchApiClientFactory factory;

    @Mock
    private BatchApiPort adapter;

    private BatchStatusSyncWorker worker;

    @BeforeEach
    void setUp() {
        worker = new BatchStatusSyncWorker(repository, factory);
    }

    @Test
    void marksBatchFailedWhenExternalBatchIdIsMissing() {
        Batch batch = inProgressBatch("claude-3-5-sonnet-20240620", "external-1");
        batch.setExternalBatchId(" ");
        when(repository.findById(1L)).thenReturn(Optional.of(batch));

        worker.syncOne(1L);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(batch.getErrorMessage()).contains("External batch id is missing for batch: 1");
        verify(repository).save(batch);
        verifyNoInteractions(factory);
    }

    @Test
    void marksBatchCompletedWhenExternalStatusIsCompleted() {
        Batch batch = inProgressBatch("claude-3-5-sonnet-20240620", "external-1");
        BatchPrompt prompt = batch.getPrompts().get(0);
        ReflectionTestUtils.setField(prompt, "id", 101L);
        when(repository.findById(1L)).thenReturn(Optional.of(batch));
        when(factory.getAdapter(batch.getModel())).thenReturn(adapter);
        when(adapter.fetchStatus(any(ExternalBatchId.class)))
                .thenReturn(new BatchStatusResult(org.jh.batchbridge.dto.external.BatchStatus.COMPLETED, null));
        when(adapter.fetchResults(any(ExternalBatchId.class), any()))
                .thenReturn(Map.of(101L, new PromptResult(true, "done", null)));

        worker.syncOne(1L);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(batch.getPrompts().get(0).getResponseContent()).isEqualTo("done");
        verify(repository).save(batch);
    }

    @Test
    void marksBatchFailedWhenExternalStatusIsFailed() {
        Batch batch = inProgressBatch("claude-3-5-sonnet-20240620", "external-1");
        when(repository.findById(1L)).thenReturn(Optional.of(batch));
        when(factory.getAdapter(batch.getModel())).thenReturn(adapter);
        when(adapter.fetchStatus(any(ExternalBatchId.class)))
                .thenReturn(new BatchStatusResult(org.jh.batchbridge.dto.external.BatchStatus.FAILED, "bad request"));

        worker.syncOne(1L);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(batch.getErrorMessage()).isEqualTo("bad request");
        verify(repository).save(batch);
    }

    private Batch inProgressBatch(String model, String externalBatchId) {
        Batch batch = new Batch("label", model);
        ReflectionTestUtils.setField(batch, "id", 1L);
        batch.addPrompt(new BatchPrompt("prompt-1", "system", "user"));
        batch.submit(externalBatchId);
        return batch;
    }
}
