package org.jh.batchbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.dto.request.PromptAddRequest;
import org.jh.batchbridge.dto.request.PromptUpdateRequest;
import org.jh.batchbridge.dto.response.BatchPromptResponse;
import org.jh.batchbridge.exception.BatchNotEditableException;
import org.jh.batchbridge.exception.BatchNotFoundException;
import org.jh.batchbridge.exception.PromptNotFoundException;
import org.jh.batchbridge.repository.BatchPromptRepository;
import org.jh.batchbridge.repository.BatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PromptServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private BatchPromptRepository promptRepository;

    @InjectMocks
    private PromptService promptService;

    private Batch draftBatch;
    private Batch completedBatch;

    @BeforeEach
    void setUp() {
        draftBatch = new Batch("draft", "model");
        ReflectionTestUtils.setField(draftBatch, "id", 1L);

        completedBatch = new Batch("completed", "model");
        ReflectionTestUtils.setField(completedBatch, "id", 2L);
        ReflectionTestUtils.setField(completedBatch, "status", BatchStatus.COMPLETED);
    }

    @Test
    void addPrompt_Success() {
        PromptAddRequest request = new PromptAddRequest("new-label", "sys", "user");
        when(batchRepository.findById(1L)).thenReturn(Optional.of(draftBatch));
        when(promptRepository.save(any(BatchPrompt.class))).thenAnswer(invocation -> {
            BatchPrompt p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 100L);
            return p;
        });

        BatchPromptResponse response = promptService.addPrompt(1L, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.label()).isEqualTo("new-label");
        verify(promptRepository).save(any(BatchPrompt.class));
    }

    @Test
    void addPrompt_BatchNotEditable_ThrowsException() {
        PromptAddRequest request = new PromptAddRequest("label", "sys", "user");
        when(batchRepository.findById(2L)).thenReturn(Optional.of(completedBatch));

        assertThatThrownBy(() -> promptService.addPrompt(2L, request))
                .isInstanceOf(BatchNotEditableException.class);
    }

    @Test
    void updatePrompt_Success() {
        BatchPrompt prompt = BatchPrompt.builder()
                .id(10L)
                .batch(draftBatch)
                .label("old")
                .userPrompt("old")
                .build();
        PromptUpdateRequest request = new PromptUpdateRequest("new-label", null, "new-user");

        when(promptRepository.findByIdAndBatchId(10L, 1L)).thenReturn(Optional.of(prompt));
        when(promptRepository.save(any(BatchPrompt.class))).thenReturn(prompt);

        BatchPromptResponse response = promptService.updatePrompt(1L, 10L, request);

        assertThat(response.label()).isEqualTo("new-label");
        assertThat(response.userPrompt()).isEqualTo("new-user");
    }

    @Test
    void updatePrompt_WrongBatch_ThrowsException() {
        PromptUpdateRequest request = new PromptUpdateRequest("new", null, "new");

        when(promptRepository.findByIdAndBatchId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promptService.updatePrompt(1L, 10L, request))
                .isInstanceOf(PromptNotFoundException.class);
    }

    @Test
    void deletePrompt_Success() {
        BatchPrompt prompt = BatchPrompt.builder()
                .id(10L)
                .batch(draftBatch)
                .build();

        when(promptRepository.findByIdAndBatchId(10L, 1L)).thenReturn(Optional.of(prompt));

        promptService.deletePrompt(1L, 10L);

        verify(promptRepository).delete(prompt);
    }
}
