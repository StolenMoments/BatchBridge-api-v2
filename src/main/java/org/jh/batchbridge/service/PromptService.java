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
import org.jh.batchbridge.repository.BatchPromptRepository;
import org.jh.batchbridge.repository.BatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromptService {

    private final BatchRepository batchRepository;
    private final BatchPromptRepository promptRepository;

    @Transactional
    public BatchPromptResponse addPrompt(Long batchId, PromptAddRequest request) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));

        if (!batch.isEditable()) {
            throw new BatchNotEditableException("Batch is not editable");
        }

        BatchPrompt prompt = BatchPrompt.builder()
                .label(resolveLabel(request.label(), batch))
                .systemPrompt(request.systemPrompt())
                .userPrompt(request.userPrompt())
                .status(PromptStatus.PENDING)
                .build();

        batch.addPrompt(prompt);

        return BatchPromptResponse.from(promptRepository.save(prompt));
    }

    @Transactional
    public BatchPromptResponse updatePrompt(Long batchId, Long promptId, PromptUpdateRequest request) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));

        if (!batch.isEditable()) {
            throw new BatchNotEditableException("Batch is not editable");
        }

        BatchPrompt prompt = promptRepository.findByIdAndBatchId(promptId, batchId)
                .orElseThrow(() -> new PromptNotFoundException(promptId));

        if (request.label() != null) prompt.setLabel(request.label());
        if (request.systemPrompt() != null) prompt.setSystemPrompt(request.systemPrompt());
        if (request.userPrompt() != null) prompt.setUserPrompt(request.userPrompt());

        return BatchPromptResponse.from(promptRepository.save(prompt));
    }

    @Transactional
    public void deletePrompt(Long batchId, Long promptId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));

        if (!batch.isEditable()) {
            throw new BatchNotEditableException("Batch is not editable");
        }

        BatchPrompt prompt = promptRepository.findByIdAndBatchId(promptId, batchId)
                .orElseThrow(() -> new PromptNotFoundException(promptId));

        promptRepository.delete(prompt);
    }

    private String resolveLabel(String label, Batch batch) {
        if (label != null && !label.isBlank()) {
            return label;
        }
        int count = batch.getPromptCount();
        return "프롬프트 " + (count + 1);
    }
}
