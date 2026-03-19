package org.jh.batchbridge.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.request.BatchCreateRequest;
import org.jh.batchbridge.dto.response.BatchDetailResponse;
import org.jh.batchbridge.dto.response.BatchListResponse;
import org.jh.batchbridge.dto.response.BatchPromptResponse;
import org.jh.batchbridge.dto.response.BatchSummaryResponse;
import org.jh.batchbridge.exception.BatchNotFoundException;
import org.jh.batchbridge.exception.ExternalApiException;
import org.jh.batchbridge.factory.BatchApiClientFactory;
import org.jh.batchbridge.repository.BatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchService {

    private static final DateTimeFormatter AUTO_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String FALLBACK_EXTERNAL_ERROR_MESSAGE = "External batch processing failed";

    private final BatchRepository batchRepository;
    private final BatchApiClientFactory batchApiClientFactory;

    public BatchService(BatchRepository batchRepository, BatchApiClientFactory batchApiClientFactory) {
        this.batchRepository = batchRepository;
        this.batchApiClientFactory = batchApiClientFactory;
    }

    @Transactional
    public BatchDetailResponse createBatch(BatchCreateRequest request) {
        String label = resolveLabel(request.label());

        Batch batch = new Batch(label, request.model());
        batch.addPrompt(new BatchPrompt(label + "-prompt-1", request.systemPrompt(), request.userPrompt()));
        batch = batchRepository.save(batch);

        try {
            BatchApiPort adapter = batchApiClientFactory.getAdapter(batch.getModel());
            BatchPrompt prompt = requireFirstPrompt(batch);
            ExternalBatchId externalBatchId = adapter.submitBatch(new BatchSubmitRequest(
                    String.valueOf(batch.getId()),
                    batch.getModel(),
                    prompt.getSystemPrompt(),
                    prompt.getUserPrompt()
            ));

            batch.setExternalBatchId(externalBatchId.value());
            batch.markInProgress();
            return toDetailResponse(batch);
        } catch (Exception e) {
            batch.failOnSubmission("Failed to submit batch to external API: " + e.getMessage());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public BatchListResponse getList(BatchStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Batch> batchPage = status == null
                ? batchRepository.findAll(pageable)
                : batchRepository.findAllByStatus(status, pageable);

        List<BatchSummaryResponse> content = batchPage.getContent().stream()
                .map(this::toSummaryResponse)
                .toList();

        return new BatchListResponse(
                content,
                batchPage.getTotalElements(),
                batchPage.getTotalPages(),
                batchPage.getNumber(),
                batchPage.getSize()
        );
    }

    @Transactional(readOnly = true)
    public BatchDetailResponse getDetail(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));
        return toDetailResponse(batch);
    }

    @Transactional
    public BatchDetailResponse syncStatus(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));

        if (batch.getStatus() != BatchStatus.IN_PROGRESS) {
            return toDetailResponse(batch);
        }
        if (batch.getExternalBatchId() == null || batch.getExternalBatchId().isBlank()) {
            throw new ExternalApiException("External batch id is missing for batch: " + id);
        }

        BatchApiPort adapter = batchApiClientFactory.getAdapter(batch.getModel());
        ExternalBatchId externalBatchId = new ExternalBatchId(batch.getExternalBatchId());
        BatchStatusResult statusResult = adapter.fetchStatus(externalBatchId);

        if (statusResult.status() == org.jh.batchbridge.dto.external.BatchStatus.COMPLETED) {
            String result = adapter.fetchResult(externalBatchId);
            batch.complete(result);
        } else if (statusResult.status() == org.jh.batchbridge.dto.external.BatchStatus.FAILED) {
            String errorMessage = (statusResult.errorMessage() == null || statusResult.errorMessage().isBlank())
                    ? FALLBACK_EXTERNAL_ERROR_MESSAGE
                    : statusResult.errorMessage();
            batch.fail(errorMessage);
        }

        return toDetailResponse(batch);
    }

    private String resolveLabel(String label) {
        if (label != null && !label.isBlank()) {
            return label.trim();
        }
        return "batch-" + AUTO_LABEL_FORMATTER.format(LocalDateTime.now());
    }

    private BatchSummaryResponse toSummaryResponse(Batch batch) {
        return new BatchSummaryResponse(
                batch.getId(),
                batch.getLabel(),
                batch.getModel(),
                batch.getStatus(),
                batch.getCreatedAt(),
                batch.getCompletedAt()
        );
    }

    private BatchDetailResponse toDetailResponse(Batch batch) {
        List<BatchPromptResponse> promptResponses = batch.getPrompts().stream()
                .map(BatchPromptResponse::from)
                .toList();

        return new BatchDetailResponse(
                batch.getId(),
                batch.getLabel(),
                batch.getModel(),
                batch.getStatus(),
                promptResponses,
                batch.getErrorMessage(),
                batch.getCreatedAt(),
                batch.getCompletedAt()
        );
    }

    private BatchPrompt requireFirstPrompt(Batch batch) {
        BatchPrompt prompt = batch.getFirstPrompt();
        if (prompt == null) {
            throw new IllegalStateException("Batch prompt is missing for batch: " + batch.getId());
        }
        return prompt;
    }
}
