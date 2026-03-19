package org.jh.batchbridge.scheduler;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.repository.BatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BatchStatusSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchStatusSyncScheduler.class);

    private final BatchRepository repository;
    private final BatchStatusSyncWorker worker;

    public BatchStatusSyncScheduler(BatchRepository repository, BatchStatusSyncWorker worker) {
        this.repository = repository;
        this.worker = worker;
    }

    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void syncInProgressBatches() {
        List<Batch> targets = repository.findAllByStatus(BatchStatus.IN_PROGRESS);
        if (targets.isEmpty()) {
            log.info("Batch sync skipped: no IN_PROGRESS batches");
            return;
        }

        log.info("Batch sync started: {} IN_PROGRESS batches", targets.size());
        for (Batch batch : targets) {
            try {
                worker.syncOne(batch.getId());
            } catch (Exception e) {
                log.error("Batch sync failed [id={}]: {}", batch.getId(), e.getMessage(), e);
            }
        }
        log.info("Batch sync finished");
    }
}
