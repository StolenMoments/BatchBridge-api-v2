package org.jh.batchbridge.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.domain.BatchRequest;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.request.BatchCreateRequest;
import org.jh.batchbridge.dto.response.BatchDetailResponse;
import org.jh.batchbridge.dto.response.BatchListResponse;
import org.jh.batchbridge.dto.response.BatchSummaryResponse;
import org.jh.batchbridge.exception.BatchNotFoundException;
import org.jh.batchbridge.exception.ExternalApiException;
import org.jh.batchbridge.factory.BatchApiClientFactory;
import org.jh.batchbridge.repository.BatchRequestRepository;
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

    private final BatchRequestRepository batchRequestRepository;
    private final BatchApiClientFactory batchApiClientFactory;

    public BatchService(BatchRequestRepository batchRequestRepository, BatchApiClientFactory batchApiClientFactory) {
        this.batchRequestRepository = batchRequestRepository;
        this.batchApiClientFactory = batchApiClientFactory;
    }

    public BatchDetailResponse createBatch(BatchCreateRequest request) {
        String label = resolveLabel(request.label());

        BatchRequest batchRequest = new BatchRequest(
                label,
                request.model(),
                request.systemPrompt(),
                request.userPrompt()
        );
        batchRequest = saveBatch(batchRequest);

        try {
            BatchApiPort adapter = batchApiClientFactory.getAdapter(batchRequest.getModel());
            ExternalBatchId externalBatchId = adapter.submitBatch(new BatchSubmitRequest(
                    String.valueOf(batchRequest.getId()),
                    batchRequest.getModel(),
                    batchRequest.getSystemPrompt(),
                    batchRequest.getUserPrompt()
            ));

            batchRequest.setExternalBatchId(externalBatchId.value());
            batchRequest.markInProgress();
            return toDetailResponse(saveBatch(batchRequest));
        } catch (Exception e) {
            batchRequest.fail("Failed to submit batch to external API: " + e.getMessage());
            saveBatch(batchRequest);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public BatchListResponse getList(BatchStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<BatchRequest> batchPage = status == null
                ? batchRequestRepository.findAll(pageable)
                : batchRequestRepository.findAllByStatus(status, pageable);

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
        BatchRequest batchRequest = batchRequestRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));
        return toDetailResponse(batchRequest);
    }

    public BatchDetailResponse syncStatus(Long id) {
        BatchRequest batchRequest = batchRequestRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));

        if (batchRequest.getStatus() == BatchStatus.COMPLETED || batchRequest.getStatus() == BatchStatus.FAILED) {
            return toDetailResponse(batchRequest);
        }
        if (batchRequest.getStatus() != BatchStatus.IN_PROGRESS) {
            return toDetailResponse(batchRequest);
        }
        if (batchRequest.getExternalBatchId() == null || batchRequest.getExternalBatchId().isBlank()) {
            throw new ExternalApiException("External batch id is missing for batch: " + id);
        }

        BatchApiPort adapter = batchApiClientFactory.getAdapter(batchRequest.getModel());
        ExternalBatchId externalBatchId = new ExternalBatchId(batchRequest.getExternalBatchId());
        
        // 외부 API 호출은 트랜잭션 밖에서 수행
        BatchStatusResult statusResult = adapter.fetchStatus(externalBatchId);

        if (statusResult.status() == org.jh.batchbridge.dto.external.BatchStatus.COMPLETED) {
            String result = adapter.fetchResult(externalBatchId);
            updateToCompleted(id, result);
        } else if (statusResult.status() == org.jh.batchbridge.dto.external.BatchStatus.FAILED) {
            String errorMessage = (statusResult.errorMessage() == null || statusResult.errorMessage().isBlank())
                    ? FALLBACK_EXTERNAL_ERROR_MESSAGE
                    : statusResult.errorMessage();
            updateToFailed(id, errorMessage);
        }

        return getDetail(id);
    }

    @Transactional
    public BatchRequest saveBatch(BatchRequest batchRequest) {
        return batchRequestRepository.save(batchRequest);
    }

    @Transactional
    public void updateToCompleted(Long id, String result) {
        BatchRequest batchRequest = batchRequestRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));
        batchRequest.complete(result);
        batchRequestRepository.save(batchRequest);
    }

    @Transactional
    public void updateToFailed(Long id, String errorMessage) {
        BatchRequest batchRequest = batchRequestRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));
        batchRequest.fail(errorMessage);
        batchRequestRepository.save(batchRequest);
    }

    private String resolveLabel(String label) {
        if (label != null && !label.isBlank()) {
            return label.trim();
        }
        return "batch-" + AUTO_LABEL_FORMATTER.format(LocalDateTime.now());
    }

    private BatchSummaryResponse toSummaryResponse(BatchRequest batchRequest) {
        return new BatchSummaryResponse(
                batchRequest.getId(),
                batchRequest.getLabel(),
                batchRequest.getModel(),
                batchRequest.getStatus(),
                batchRequest.getCreatedAt(),
                batchRequest.getCompletedAt()
        );
    }

    private BatchDetailResponse toDetailResponse(BatchRequest batchRequest) {
        return new BatchDetailResponse(
                batchRequest.getId(),
                batchRequest.getLabel(),
                batchRequest.getModel(),
                batchRequest.getStatus(),
                batchRequest.getSystemPrompt(),
                batchRequest.getUserPrompt(),
                batchRequest.getResponseContent(),
                batchRequest.getErrorMessage(),
                batchRequest.getCreatedAt(),
                batchRequest.getCompletedAt()
        );
    }
}
