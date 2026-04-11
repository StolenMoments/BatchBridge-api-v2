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

    @Test
    void continuesProcessingAfterWorkerException() {
        Batch first = Batch.createDraft("first", "model");
        Batch second = Batch.createDraft("second", "model");
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        when(repository.findAllByStatus(BatchStatus.IN_PROGRESS, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        org.mockito.Mockito.doThrow(new RuntimeException("worker failed"))
                .when(worker).syncOne(1L);

        scheduler.syncInProgressBatches();

        // first가 실패해도 second는 처리됨
        verify(worker, times(1)).syncOne(2L);
    }

    @Test
    void handlesPaginationAcrossMultiplePages() {
        Batch pageOneBatch = Batch.createDraft("b1", "model");
        Batch pageTwoBatch = Batch.createDraft("b2", "model");
        ReflectionTestUtils.setField(pageOneBatch, "id", 1L);
        ReflectionTestUtils.setField(pageTwoBatch, "id", 2L);

        // 첫 페이지: hasNext=true
        PageImpl<Batch> pageOne = new PageImpl<>(List.of(pageOneBatch), PageRequest.of(0, 100), 200);
        // 두 번째 페이지: hasNext=false
        PageImpl<Batch> pageTwo = new PageImpl<>(List.of(pageTwoBatch), PageRequest.of(1, 100), 200);

        when(repository.findAllByStatus(BatchStatus.IN_PROGRESS, PageRequest.of(0, 100))).thenReturn(pageOne);
        when(repository.findAllByStatus(BatchStatus.IN_PROGRESS, PageRequest.of(1, 100))).thenReturn(pageTwo);

        scheduler.syncInProgressBatches();

        verify(worker, times(1)).syncOne(1L);
        verify(worker, times(1)).syncOne(2L);
    }
}
