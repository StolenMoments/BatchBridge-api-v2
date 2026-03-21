package org.jh.batchbridge.scheduler;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.repository.BatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BatchStatusSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchStatusSyncScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final BatchRepository repository;
    private final BatchStatusSyncWorker worker;

    public BatchStatusSyncScheduler(BatchRepository repository, BatchStatusSyncWorker worker) {
        this.repository = repository;
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${batch-bridge.sync.interval-minutes:2}", timeUnit = TimeUnit.MINUTES)
    public void syncInProgressBatches() {
        log.info("Batch sync started");
        int page = 0;
        long processedCount = 0;

        while (true) {
            Page<Batch> targets = repository.findAllByStatus(BatchStatus.IN_PROGRESS, PageRequest.of(page, BATCH_SIZE));
            if (targets.isEmpty()) {
                break;
            }

            for (Batch batch : targets) {
                try {
                    worker.syncOne(batch.getId());
                    processedCount++;
                } catch (Exception e) {
                    log.error("Batch sync failed [id={}]: {}", batch.getId(), e.getMessage(), e);
                }
            }

            if (!targets.hasNext()) {
                break;
            }
            page++;
        }

        if (processedCount == 0) {
            log.info("Batch sync skipped: no IN_PROGRESS batches");
        } else {
            log.info("Batch sync finished: {} batches processed", processedCount);
        }
    }
}
