package org.jh.batchbridge.service;

import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptStatus;
import org.jh.batchbridge.dto.request.PromptAddRequest;
import org.jh.batchbridge.dto.request.PromptUpdateRequest;
import org.jh.batchbridge.dto.response.BatchPromptResponse;
import org.jh.batchbridge.exception.BatchNotEditableException;
import org.jh.batchbridge.exception.BatchNotFoundException;
import org.jh.batchbridge.exception.PromptNotFoundException;
import org.jh.batchbridge.exception.ReferencePromptNotCompletedException;
import org.jh.batchbridge.repository.BatchPromptRepository;
import org.jh.batchbridge.repository.BatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.jh.batchbridge.domain.PromptAttachment;
import org.jh.batchbridge.domain.PromptType;
import org.jh.batchbridge.dto.request.PromptAttachmentRequest;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromptService {

    private static final int MAX_ATTACHMENT_CONTENT_SIZE = 1_000_000; // 1MB limit for example

    private final BatchRepository batchRepository;
    private final BatchPromptRepository promptRepository;

    @Transactional
    public BatchPromptResponse addPrompt(Long batchId, PromptAddRequest request) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));

        if (!batch.isEditable()) {
            throw new BatchNotEditableException("Batch is not editable");
        }

        validateAttachments(request.attachments());

        List<PromptAttachment> attachments = null;
        if (request.attachments() != null) {
            attachments = request.attachments().stream()
                    .map(req -> PromptAttachment.create(req.fileName(), req.fileContent()))
                    .toList();
        }

        PromptType promptType = request.promptType() != null ? request.promptType() : PromptType.TEXT;
        String resolvedReferenceMediaUrl = resolveReferenceMediaUrl(
                batchId, promptType, request.referenceMediaUrl(), request.referencePromptId());
        BatchPrompt prompt = BatchPrompt.create(
                resolveLabel(request.label(), batch),
                request.systemPrompt(),
                request.userPrompt(),
                promptType,
                resolvedReferenceMediaUrl,
                request.referencePromptId(),
                attachments
        );

        batch.addPrompt(prompt);

        return BatchPromptResponse.from(promptRepository.save(prompt));
    }

    private void validateAttachments(List<PromptAttachmentRequest> attachments) {
        if (attachments == null) return;
        for (PromptAttachmentRequest attachment : attachments) {
            if (attachment.fileContent().length() > MAX_ATTACHMENT_CONTENT_SIZE) {
                throw new IllegalArgumentException("Attachment content too large: " + attachment.fileName());
            }
        }
    }

    @Transactional
    public BatchPromptResponse updatePrompt(Long batchId, Long promptId, PromptUpdateRequest request) {
        BatchPrompt prompt = promptRepository.findByIdAndBatchId(promptId, batchId)
                .orElseThrow(() -> new PromptNotFoundException(promptId));

        Batch batch = prompt.getBatch();
        if (!batch.isEditable()) {
            throw new BatchNotEditableException("Batch is not editable");
        }

        validateAttachments(request.attachments());

        String label = request.label() != null ? request.label() : prompt.getLabel();
        String systemPrompt = request.systemPrompt() != null ? request.systemPrompt() : prompt.getSystemPrompt();
        String userPrompt = request.userPrompt() != null ? request.userPrompt() : prompt.getUserPrompt();

        List<PromptAttachment> attachments = null;
        if (request.attachments() != null) {
            attachments = request.attachments().stream()
                    .map(req -> PromptAttachment.create(req.fileName(), req.fileContent()))
                    .toList();
        }

        PromptType promptType = request.promptType() != null ? request.promptType() : prompt.getPromptType();
        String resolvedReferenceMediaUrl;
        Long resolvedReferencePromptId;
        if (request.referencePromptId() == null && request.referenceMediaUrl() == null) {
            // 참조 필드 변경 의도 없음 → 기존 값 유지, 재검증 스킵
            resolvedReferenceMediaUrl = prompt.getReferenceMediaUrl();
            resolvedReferencePromptId = prompt.getReferencePromptId();
        } else {
            resolvedReferenceMediaUrl = resolveReferenceMediaUrl(
                    batchId, promptType, request.referenceMediaUrl(), request.referencePromptId());
            resolvedReferencePromptId = request.referencePromptId();
        }
        prompt.update(label, systemPrompt, userPrompt, promptType, resolvedReferenceMediaUrl, resolvedReferencePromptId, attachments);

        return BatchPromptResponse.from(promptRepository.save(prompt));
    }

    @Transactional
    public void deletePrompt(Long batchId, Long promptId) {
        BatchPrompt prompt = promptRepository.findByIdAndBatchId(promptId, batchId)
                .orElseThrow(() -> new PromptNotFoundException(promptId));

        if (!prompt.getBatch().isEditable()) {
            throw new BatchNotEditableException("Batch is not editable");
        }

        promptRepository.delete(prompt);
    }

    public BatchPromptResponse getPrompt(Long batchId, Long promptId) {
        BatchPrompt prompt = promptRepository.findByIdAndBatchId(promptId, batchId)
                .orElseThrow(() -> new PromptNotFoundException(promptId));
        return BatchPromptResponse.from(prompt);
    }

    private String resolveReferenceMediaUrl(Long batchId, PromptType promptType,
                                             String referenceMediaUrl, Long referencePromptId) {
        if (referencePromptId == null) return referenceMediaUrl;

        if (promptType != PromptType.IMAGE_EDIT && promptType != PromptType.VIDEO_EDIT) {
            throw new IllegalArgumentException("referencePromptId is only allowed for IMAGE_EDIT or VIDEO_EDIT");
        }
        if (referenceMediaUrl != null) {
            throw new IllegalArgumentException("referenceMediaUrl and referencePromptId cannot be specified together");
        }
        BatchPrompt refPrompt = promptRepository.findByIdAndBatchId(referencePromptId, batchId)
                .orElseThrow(() -> new PromptNotFoundException(referencePromptId));
        if (refPrompt.getResultMediaPath() == null) {
            throw new ReferencePromptNotCompletedException(referencePromptId);
        }
        return "/api/media/" + batchId + "/" + referencePromptId;
    }

    private String resolveLabel(String label, Batch batch) {
        if (label != null && !label.isBlank()) {
            return label;
        }
        int count = batch.getPromptCount();
        return "프롬프트 " + (count + 1);
    }
}
