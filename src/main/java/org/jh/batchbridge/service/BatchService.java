package org.jh.batchbridge.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.ExternalBatchStatus;
import org.jh.batchbridge.dto.request.BatchCreateRequest;
import org.jh.batchbridge.dto.request.BatchUpdateRequest;
import org.jh.batchbridge.dto.response.BatchDetailResponse;
import org.jh.batchbridge.dto.response.BatchListResponse;
import org.jh.batchbridge.dto.response.BatchSubmitResponse;
import org.jh.batchbridge.dto.response.BatchSyncPromptsResponse;
import org.jh.batchbridge.dto.response.BatchSummaryResponse;
import org.jh.batchbridge.exception.BatchEmptyException;
import org.jh.batchbridge.exception.BatchNotEditableException;
import org.jh.batchbridge.exception.BatchNotFoundException;
import org.jh.batchbridge.exception.BatchNotSyncedException;
import org.jh.batchbridge.exception.UnsupportedPromptTypeException;
import org.jh.batchbridge.domain.PromptStatus;
import org.jh.batchbridge.factory.BatchApiClientFactory;
import org.jh.batchbridge.repository.BatchRepository;
import org.jh.batchbridge.repository.BatchSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;
import org.jh.batchbridge.domain.PromptAttachment;

@Service
public class BatchService {

    private static final int MAX_ATTACHMENT_CONTENT_SIZE = 1_000_000;

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

        BatchCreateRequest.PromptPayload promptPayload = request.prompt();
        String promptLabel = (promptPayload.label() != null && !promptPayload.label().isBlank())
                ? promptPayload.label()
                : "Prompt 1";

        List<PromptAttachment> attachments = null;
        if (promptPayload.attachments() != null) {
            for (var attachmentReq : promptPayload.attachments()) {
                if (attachmentReq.fileContent().length() > MAX_ATTACHMENT_CONTENT_SIZE) {
                    throw new IllegalArgumentException("Attachment content too large: " + attachmentReq.fileName());
                }
            }
            attachments = promptPayload.attachments().stream()
                    .map(req -> PromptAttachment.create(req.fileName(), req.fileContent()))
                    .toList();
        }

        BatchPrompt prompt = BatchPrompt.create(
                promptLabel,
                promptPayload.systemPrompt(),
                promptPayload.userPrompt(),
                attachments
        );
        batch.addPrompt(prompt);

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
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new BatchNotFoundException(id));
        return BatchDetailResponse.from(batch);
    }

    @Transactional
    public BatchDetailResponse updateBatch(Long id, BatchUpdateRequest request) {
        String label = (request.label() != null && !request.label().isBlank()) ? request.label() : null;
        String model = request.model();

        if (label == null && model == null) {
            throw new IllegalArgumentException("At least one of label or model must be provided.");
        }

        Batch batch = batchRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new BatchNotFoundException(id));

        if (!batch.isEditable()) {
            throw new BatchNotEditableException("Only DRAFT batches can be edited.");
        }

        if (model != null) {
            batchApiClientFactory.getAdapter(model);
        }

        batch.update(label, model);
        return BatchDetailResponse.from(batch);
    }

    @Transactional
    public void deleteBatch(Long id) {
        Batch batch = batchRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new BatchNotFoundException(id));

        if (!batch.isEditable()) {
            throw new BatchNotEditableException("Only DRAFT batches can be deleted.");
        }

        batch.delete();
    }

    @Transactional
    public BatchSubmitResponse submitBatch(Long id) {
        Batch batch = batchRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new BatchNotFoundException(id));

        if (!batch.isEditable()) {
            throw new BatchNotEditableException("Batch is not editable");
        }
        if (batch.getPrompts().isEmpty()) {
            throw new BatchEmptyException("Batch has no prompts");
        }

        BatchApiPort adapter = batchApiClientFactory.getAdapter(batch.getModel());

        for (BatchPrompt prompt : batch.getPrompts()) {
            if (!adapter.supportsPromptType(prompt.getPromptType())) {
                throw new UnsupportedPromptTypeException(prompt.getPromptType());
            }
        }

        BatchSubmitRequest submitRequest = BatchSubmitRequest.from(batch);
        ExternalBatchId externalId = adapter.submitBatch(submitRequest);

        batch.submit(externalId.value());

        return BatchSubmitResponse.from(batch);
    }

    @Transactional
    public BatchDetailResponse syncStatus(Long id) {
        Batch batch = batchRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
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

    @Transactional
    public BatchSyncPromptsResponse syncPrompts(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));

        if (batch.getStatus() != BatchStatus.COMPLETED) {
            throw new BatchNotSyncedException("Only COMPLETED batches can have prompts resynced. Current status: " + batch.getStatus());
        }

        List<BatchPrompt> targetPrompts = batch.getPrompts().stream()
                .filter(p -> p.getStatus() == PromptStatus.PENDING || p.getStatus() == PromptStatus.FAILED)
                .toList();

        if (targetPrompts.isEmpty()) {
            return new BatchSyncPromptsResponse(id, 0, 0);
        }

        BatchApiPort adapter = batchApiClientFactory.getAdapter(batch.getModel());
        ExternalBatchId externalBatchId = new ExternalBatchId(batch.getExternalBatchId());
        Map<Long, PromptResult> results = adapter.fetchResults(externalBatchId, targetPrompts);

        int resynced = 0;
        int stillFailed = 0;

        for (BatchPrompt prompt : targetPrompts) {
            PromptResult result = results.get(prompt.getId());
            if (result != null && result.success()) {
                prompt.complete(result.responseContent());
                resynced++;
            } else {
                String errorMsg = (result != null && result.errorMessage() != null && !result.errorMessage().isBlank())
                        ? result.errorMessage()
                        : "No result found for prompt";
                prompt.fail(errorMsg);
                stillFailed++;
            }
        }

        return new BatchSyncPromptsResponse(id, resynced, stillFailed);
    }
}
