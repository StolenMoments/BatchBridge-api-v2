package org.jh.batchbridge.scheduler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.repository.BatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BatchStatusSyncSchedulerTest {

    @Mock
    private BatchRepository repository;

    @Mock
    private BatchStatusSyncWorker worker;

    private BatchStatusSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BatchStatusSyncScheduler(repository, worker);
    }

    @Test
    void skipsWhenThereAreNoInProgressBatches() {
        when(repository.findAllByStatus(BatchStatus.IN_PROGRESS, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of()));

        scheduler.syncInProgressBatches();

        verifyNoInteractions(worker);
    }

    @Test
    void delegatesEveryBatchToWorker() {
        Batch first = Batch.createDraft("first", "model");
        Batch second = Batch.createDraft("second", "model");
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        when(repository.findAllByStatus(BatchStatus.IN_PROGRESS, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        scheduler.syncInProgressBatches();

        verify(worker, times(1)).syncOne(1L);
        verify(worker, times(1)).syncOne(2L);
    }
}
