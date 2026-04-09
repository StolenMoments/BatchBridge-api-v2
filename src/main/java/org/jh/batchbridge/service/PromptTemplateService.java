package org.jh.batchbridge.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.domain.PromptTemplate;
import org.jh.batchbridge.dto.request.PromptTemplateCreateRequest;
import org.jh.batchbridge.dto.request.PromptTemplateUpdateRequest;
import org.jh.batchbridge.dto.response.PromptTemplateResponse;
import org.jh.batchbridge.exception.PromptTemplateNotFoundException;
import org.jh.batchbridge.repository.PromptTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromptTemplateService {

    private final PromptTemplateRepository promptTemplateRepository;

    public List<PromptTemplateResponse> getAll() {
        return promptTemplateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(PromptTemplateResponse::from)
                .toList();
    }

    public PromptTemplateResponse getOne(Long id) {
        PromptTemplate template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new PromptTemplateNotFoundException(id));
        return PromptTemplateResponse.from(template);
    }

    @Transactional
    public PromptTemplateResponse create(PromptTemplateCreateRequest request) {
        PromptTemplate template = PromptTemplate.builder()
                .name(request.name())
                .description(request.description())
                .systemPrompt(request.systemPrompt())
                .userPrompt(request.userPrompt())
                .build();
        return PromptTemplateResponse.from(promptTemplateRepository.save(template));
    }

    @Transactional
    public PromptTemplateResponse update(Long id, PromptTemplateUpdateRequest request) {
        PromptTemplate template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new PromptTemplateNotFoundException(id));
        template.update(request.name(), request.description(), request.systemPrompt(), request.userPrompt());
        return PromptTemplateResponse.from(template);
    }

    @Transactional
    public void delete(Long id) {
        PromptTemplate template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new PromptTemplateNotFoundException(id));
        promptTemplateRepository.delete(template);
    }
}
