package org.jh.batchbridge.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.ExternalBatchStatus;
import org.jh.batchbridge.dto.request.BatchCreateRequest;
import org.jh.batchbridge.dto.response.BatchDetailResponse;
import org.jh.batchbridge.dto.response.BatchListResponse;
import org.jh.batchbridge.dto.response.BatchSubmitResponse;
import org.jh.batchbridge.dto.response.BatchSummaryResponse;
import org.jh.batchbridge.exception.BatchEmptyException;
import org.jh.batchbridge.exception.BatchNotEditableException;
import org.jh.batchbridge.exception.BatchNotFoundException;
import org.jh.batchbridge.factory.BatchApiClientFactory;
import org.jh.batchbridge.repository.BatchRepository;
import org.jh.batchbridge.repository.BatchSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchService {

    private static final String FALLBACK_EXTERNAL_ERROR_MESSAGE = "External batch processing failed";
    private static final String MISSING_EXTERNAL_BATCH_ID_MESSAGE = "External batch id is missing for batch: ";
    private static final String DEFAULT_BATCH_LABEL_PREFIX = "Batch ";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm");

    private final BatchRepository batchRepository;
    private final BatchApiClientFactory batchApiClientFactory;

    public BatchService(BatchRepository batchRepository, BatchApiClientFactory batchApiClientFactory) {
        this.batchRepository = batchRepository;
        this.batchApiClientFactory = batchApiClientFactory;
    }

    @Transactional
    public BatchDetailResponse createBatch(BatchCreateRequest request) {
        // Validate model early so unsupported values are rejected before persistence.
        batchApiClientFactory.getAdapter(request.model());

        String label = (request.label() != null && !request.label().isBlank())
                ? request.label()
                : DEFAULT_BATCH_LABEL_PREFIX + LocalDateTime.now().format(DATE_TIME_FORMATTER);

        Batch batch = Batch.createDraft(label, request.model());

        return BatchDetailResponse.from(batchRepository.save(batch));
    }

    @Transactional(readOnly = true)
    public BatchListResponse getList(BatchStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<BatchSummaryView> batchPage = batchRepository.findBatchSummaries(status, pageable);

        List<BatchSummaryResponse> content = batchPage.getContent().stream()
                .map(BatchSummaryResponse::from)
                .toList();

        return new BatchListResponse(
                content,
                batchPage.getTotalElements(),
                batchPage.getTotalPages(),
                batchPage.getNumber() + 1,
                batchPage.getSize()
        );
    }

    @Transactional(readOnly = true)
    public BatchDetailResponse getDetail(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));
        return BatchDetailResponse.from(batch);
    }

    @Transactional
    public BatchSubmitResponse submitBatch(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));

        if (!batch.isEditable()) {
            throw new BatchNotEditableException("Batch is not editable");
        }
        if (batch.getPrompts().isEmpty()) {
            throw new BatchEmptyException("Batch has no prompts");
        }

        BatchApiPort adapter = batchApiClientFactory.getAdapter(batch.getModel());
        BatchSubmitRequest submitRequest = BatchSubmitRequest.from(batch);
        ExternalBatchId externalId = adapter.submitBatch(submitRequest);

        batch.submit(externalId.value());

        return BatchSubmitResponse.from(batch);
    }

    @Transactional
    public BatchDetailResponse syncStatus(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));

        if (batch.getStatus() != BatchStatus.IN_PROGRESS) {
            return BatchDetailResponse.from(batch);
        }
        if (batch.getExternalBatchId() == null || batch.getExternalBatchId().isBlank()) {
            batch.fail(MISSING_EXTERNAL_BATCH_ID_MESSAGE + id);
            return BatchDetailResponse.from(batch);
        }

        BatchApiPort adapter = batchApiClientFactory.getAdapter(batch.getModel());
        ExternalBatchId externalBatchId = new ExternalBatchId(batch.getExternalBatchId());
        BatchStatusResult statusResult = adapter.fetchStatus(externalBatchId);

        if (statusResult.status() == ExternalBatchStatus.COMPLETED) {
            Map<Long, PromptResult> results = adapter.fetchResults(externalBatchId, batch.getPrompts());
            batch.complete(results);
        } else if (statusResult.status() == ExternalBatchStatus.FAILED) {
            String errorMessage = (statusResult.errorMessage() == null || statusResult.errorMessage().isBlank())
                    ? FALLBACK_EXTERNAL_ERROR_MESSAGE
                    : statusResult.errorMessage();
            batch.fail(errorMessage);
        }

        return BatchDetailResponse.from(batch);
    }
}
