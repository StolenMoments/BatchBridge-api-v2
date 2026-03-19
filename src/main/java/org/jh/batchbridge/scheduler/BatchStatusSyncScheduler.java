package org.jh.batchbridge.scheduler;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.factory.BatchApiClientFactory;
import org.jh.batchbridge.repository.BatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BatchStatusSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchStatusSyncScheduler.class);
    private static final String FALLBACK_EXTERNAL_ERROR_MESSAGE = "External batch processing failed";

    private final BatchRepository repository;
    private final BatchApiClientFactory factory;

    public BatchStatusSyncScheduler(BatchRepository repository, BatchApiClientFactory factory) {
        this.repository = repository;
        this.factory = factory;
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
                syncOne(batch);
            } catch (Exception e) {
                log.error("Batch sync failed [id={}]: {}", batch.getId(), e.getMessage(), e);
            }
        }
        log.info("Batch sync finished");
    }

    @Transactional
    public void syncOne(Batch batch) {
        String externalBatchId = batch.getExternalBatchId();
        if (externalBatchId == null || externalBatchId.isBlank()) {
            throw new IllegalStateException("External batch id is missing for batch: " + batch.getId());
        }

        ExternalBatchId id = new ExternalBatchId(externalBatchId);
        BatchApiPort adapter = factory.getAdapter(batch.getModel());
        BatchStatusResult result = adapter.fetchStatus(id);

        if (result.status() == org.jh.batchbridge.dto.external.BatchStatus.COMPLETED) {
            String content = adapter.fetchResult(id);
            batch.complete(content);
            repository.save(batch);
            log.info("Batch completed [id={}]", batch.getId());
            return;
        }

        if (result.status() == org.jh.batchbridge.dto.external.BatchStatus.FAILED) {
            String errorMessage = result.errorMessage();
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = FALLBACK_EXTERNAL_ERROR_MESSAGE;
            }
            batch.fail(errorMessage);
            repository.save(batch);
            log.info("Batch failed [id={}]", batch.getId());
        }
    }
}
