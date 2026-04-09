package org.jh.batchbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.jh.batchbridge.domain.PromptTemplate;
import org.jh.batchbridge.dto.request.PromptTemplateCreateRequest;
import org.jh.batchbridge.dto.request.PromptTemplateUpdateRequest;
import org.jh.batchbridge.dto.response.PromptTemplateResponse;
import org.jh.batchbridge.exception.PromptTemplateNotFoundException;
import org.jh.batchbridge.repository.PromptTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PromptTemplateServiceTest {

    @Mock
    private PromptTemplateRepository promptTemplateRepository;

    @InjectMocks
    private PromptTemplateService promptTemplateService;

    private PromptTemplate buildTemplate(Long id) {
        PromptTemplate template = PromptTemplate.builder()
                .name("test")
                .description("desc")
                .systemPrompt("sys")
                .userPrompt("user")
                .build();
        ReflectionTestUtils.setField(template, "id", id);
        return template;
    }

    @Test
    void getAll_ReturnsList() {
        when(promptTemplateRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(buildTemplate(1L), buildTemplate(2L)));

        List<PromptTemplateResponse> result = promptTemplateService.getAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void getOne_Success() {
        when(promptTemplateRepository.findById(1L)).thenReturn(Optional.of(buildTemplate(1L)));

        PromptTemplateResponse result = promptTemplateService.getOne(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("test");
    }

    @Test
    void getOne_NotFound_ThrowsException() {
        when(promptTemplateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promptTemplateService.getOne(99L))
                .isInstanceOf(PromptTemplateNotFoundException.class);
    }

    @Test
    void create_Success() {
        PromptTemplateCreateRequest request = new PromptTemplateCreateRequest("name", "desc", "sys", "user");
        when(promptTemplateRepository.save(any(PromptTemplate.class))).thenAnswer(invocation -> {
            PromptTemplate t = invocation.getArgument(0);
            ReflectionTestUtils.setField(t, "id", 10L);
            return t;
        });

        PromptTemplateResponse result = promptTemplateService.create(request);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.name()).isEqualTo("name");
        verify(promptTemplateRepository).save(any(PromptTemplate.class));
    }

    @Test
    void update_Success() {
        PromptTemplate template = buildTemplate(1L);
        PromptTemplateUpdateRequest request = new PromptTemplateUpdateRequest("updated", null, null, "new-user");
        when(promptTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        PromptTemplateResponse result = promptTemplateService.update(1L, request);

        assertThat(result.name()).isEqualTo("updated");
        assertThat(result.userPrompt()).isEqualTo("new-user");
    }

    @Test
    void update_NotFound_ThrowsException() {
        PromptTemplateUpdateRequest request = new PromptTemplateUpdateRequest("name", null, null, "user");
        when(promptTemplateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promptTemplateService.update(99L, request))
                .isInstanceOf(PromptTemplateNotFoundException.class);
    }

    @Test
    void delete_Success() {
        PromptTemplate template = buildTemplate(1L);
        when(promptTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        promptTemplateService.delete(1L);

        verify(promptTemplateRepository).delete(template);
    }

    @Test
    void delete_NotFound_ThrowsException() {
        when(promptTemplateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promptTemplateService.delete(99L))
                .isInstanceOf(PromptTemplateNotFoundException.class);
    }
}
