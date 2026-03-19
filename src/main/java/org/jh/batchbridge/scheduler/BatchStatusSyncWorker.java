package org.jh.batchbridge.scheduler;

import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.factory.BatchApiClientFactory;
import org.jh.batchbridge.repository.BatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class BatchStatusSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(BatchStatusSyncWorker.class);
    private static final String FALLBACK_EXTERNAL_ERROR_MESSAGE = "External batch processing failed";

    private final BatchRepository repository;
    private final BatchApiClientFactory factory;

    public BatchStatusSyncWorker(BatchRepository repository, BatchApiClientFactory factory) {
        this.repository = repository;
        this.factory = factory;
    }

    @Transactional
    public void syncOne(Long batchId) {
        Batch batch = repository.findById(batchId).orElse(null);
        if (batch == null) {
            log.warn("Batch not found while syncing [id={}]", batchId);
            return;
        }
        if (batch.getStatus() != BatchStatus.IN_PROGRESS) {
            return;
        }

        String externalBatchId = batch.getExternalBatchId();
        if (externalBatchId == null || externalBatchId.isBlank()) {
            String errorMessage = "External batch id is missing for batch: " + batch.getId();
            batch.fail(errorMessage);
            repository.save(batch);
            log.warn("Batch failed due to missing external batch id [id={}]", batch.getId());
            return;
        }

        ExternalBatchId id = new ExternalBatchId(externalBatchId);
        BatchApiPort adapter = factory.getAdapter(batch.getModel());
        BatchStatusResult result = adapter.fetchStatus(id);

        if (result.status() == org.jh.batchbridge.dto.external.BatchStatus.COMPLETED) {
            Map<Long, PromptResult> results = adapter.fetchResults(id, batch.getPrompts());
            batch.complete(results);
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
