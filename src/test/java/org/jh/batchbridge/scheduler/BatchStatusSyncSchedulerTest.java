package org.jh.batchbridge.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.factory.BatchApiClientFactory;
import org.jh.batchbridge.repository.BatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchStatusSyncSchedulerTest {

    @Mock
    private BatchRepository repository;

    @Mock
    private BatchApiClientFactory factory;

    @Mock
    private BatchApiPort adapter;

    private BatchStatusSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BatchStatusSyncScheduler(repository, factory);
    }

    @Test
    void skipsWhenThereAreNoInProgressBatches() {
        when(repository.findAllByStatus(BatchStatus.IN_PROGRESS)).thenReturn(List.of());

        scheduler.syncInProgressBatches();

        verifyNoInteractions(factory);
    }

    @Test
    void marksBatchCompletedAndSavesResultWhenExternalStatusIsCompleted() {
        Batch batch = inProgressBatch("claude-3-5-sonnet-20240620", "external-1");
        when(repository.findAllByStatus(BatchStatus.IN_PROGRESS)).thenReturn(List.of(batch));
        when(factory.getAdapter(batch.getModel())).thenReturn(adapter);
        when(adapter.fetchStatus(any(ExternalBatchId.class)))
                .thenReturn(new BatchStatusResult(org.jh.batchbridge.dto.external.BatchStatus.COMPLETED, null));
        when(adapter.fetchResult(any(ExternalBatchId.class))).thenReturn("done");

        scheduler.syncInProgressBatches();

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(batch.getPrompts().get(0).getResponseContent()).isEqualTo("done");
        verify(repository).save(batch);
    }

    @Test
    void continuesAfterSingleBatchFailure() {
        Batch failedTarget = inProgressBatch("claude-3-5-sonnet-20240620", "external-1");
        Batch completedTarget = inProgressBatch("claude-3-5-sonnet-20240620", "external-2");
        when(repository.findAllByStatus(BatchStatus.IN_PROGRESS)).thenReturn(List.of(failedTarget, completedTarget));
        when(factory.getAdapter("claude-3-5-sonnet-20240620")).thenReturn(adapter);
        when(adapter.fetchStatus(new ExternalBatchId("external-1")))
                .thenThrow(new RuntimeException("temporary error"));
        when(adapter.fetchStatus(new ExternalBatchId("external-2")))
                .thenReturn(new BatchStatusResult(org.jh.batchbridge.dto.external.BatchStatus.FAILED, "bad request"));

        scheduler.syncInProgressBatches();

        assertThat(failedTarget.getStatus()).isEqualTo(BatchStatus.IN_PROGRESS);
        assertThat(completedTarget.getStatus()).isEqualTo(BatchStatus.FAILED);
        verify(repository).save(completedTarget);
        verify(repository, never()).save(failedTarget);
    }

    private Batch inProgressBatch(String model, String externalBatchId) {
        Batch batch = new Batch("label", model);
        batch.addPrompt(new BatchPrompt("prompt-1", "system", "user"));
        batch.markInProgress();
        batch.setExternalBatchId(externalBatchId);
        return batch;
    }
}
