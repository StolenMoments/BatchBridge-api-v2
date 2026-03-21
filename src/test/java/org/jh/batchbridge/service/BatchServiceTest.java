package org.jh.batchbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jh.batchbridge.adapter.BatchApiPort;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.ExternalBatchStatus;
import org.jh.batchbridge.dto.request.BatchCreateRequest;
import org.jh.batchbridge.dto.response.BatchListResponse;
import org.jh.batchbridge.dto.response.BatchSubmitResponse;
import org.jh.batchbridge.exception.BatchEmptyException;
import org.jh.batchbridge.exception.BatchNotEditableException;
import org.jh.batchbridge.exception.BatchNotFoundException;
import org.jh.batchbridge.exception.UnsupportedModelException;
import org.jh.batchbridge.factory.BatchApiClientFactory;
import org.jh.batchbridge.repository.BatchRepository;
import org.jh.batchbridge.repository.BatchSummaryView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private BatchApiClientFactory batchApiClientFactory;

    @Mock
    private BatchApiPort batchApiPort;

    @InjectMocks
    private BatchService batchService;

    @Test
    void createBatch_UnsupportedModel_ThrowsAndDoesNotPersist() {
        BatchCreateRequest request = new BatchCreateRequest("label", "unknown-model");
        when(batchApiClientFactory.getAdapter("unknown-model")).thenThrow(new UnsupportedModelException("unknown-model"));

        assertThatThrownBy(() -> batchService.createBatch(request))
                .isInstanceOf(UnsupportedModelException.class);

        verify(batchApiClientFactory).getAdapter("unknown-model");
        verifyNoInteractions(batchRepository);
    }

    @Test
    void getList_MapsPromptCountFromProjection() {
        BatchSummaryView summary = new BatchSummaryView() {
            @Override
            public Long getId() {
                return 1L;
            }

            @Override
            public String getLabel() {
                return "label";
            }

            @Override
            public String getModel() {
                return "claude-3-5-sonnet-20240620";
            }

            @Override
            public BatchStatus getStatus() {
                return BatchStatus.DRAFT;
            }

            @Override
            public long getPromptCount() {
                return 3L;
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return LocalDateTime.of(2026, 3, 19, 12, 0);
            }

            @Override
            public LocalDateTime getSubmittedAt() {
                return null;
            }

            @Override
            public LocalDateTime getCompletedAt() {
                return null;
            }
        };

        when(batchRepository.findBatchSummaries(any(), argThat(pageable -> pageable.getPageNumber() == 0)))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        BatchListResponse response = batchService.getList(null, 1, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).promptCount()).isEqualTo(3);
        assertThat(response.page()).isEqualTo(1);
        verify(batchRepository).findBatchSummaries(any(), any());
    }

    @Test
    void submitBatch_SubmitsToExternalAndReturnsInProgressResponse() {
        Batch batch = Batch.createDraft("label", "claude-3-5-sonnet-20240620");
        ReflectionTestUtils.setField(batch, "id", 1L);
        BatchPrompt prompt = BatchPrompt.create("prompt-1", "system", "user");
        ReflectionTestUtils.setField(prompt, "id", 101L);
        batch.addPrompt(prompt);

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(batchApiClientFactory.getAdapter("claude-3-5-sonnet-20240620")).thenReturn(batchApiPort);
        when(batchApiPort.submitBatch(any())).thenReturn(new ExternalBatchId("msgbatch_01abc123"));

        BatchSubmitResponse response = batchService.submitBatch(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(BatchStatus.IN_PROGRESS);
        assertThat(response.externalBatchId()).isEqualTo("msgbatch_01abc123");
        assertThat(response.submittedAt()).isNotNull();

        verify(batchApiPort).submitBatch(argThat(request ->
                "claude-3-5-sonnet-20240620".equals(request.model())
                        && request.prompts().size() == 1
                        && Long.valueOf(101L).equals(request.prompts().getFirst().promptId())
                        && "system".equals(request.prompts().getFirst().systemPrompt())
                        && "user".equals(request.prompts().getFirst().userPrompt())
        ));
    }

    @Test
    void submitBatch_WhenBatchIsNotDraft_ThrowsBatchNotEditable() {
        Batch batch = Batch.createDraft("label", "claude-3-5-sonnet-20240620");
        batch.submit("ext-1");
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.submitBatch(1L))
                .isInstanceOf(BatchNotEditableException.class)
                .hasMessage("Batch is not editable");

        verifyNoInteractions(batchApiClientFactory);
    }

    @Test
    void submitBatch_WhenBatchHasNoPrompts_ThrowsBatchEmpty() {
        Batch batch = Batch.createDraft("label", "claude-3-5-sonnet-20240620");
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.submitBatch(1L))
                .isInstanceOf(BatchEmptyException.class)
                .hasMessage("Batch has no prompts");

        verifyNoInteractions(batchApiClientFactory);
    }

    @Test
    void syncStatus_MissingExternalBatchId_FailsBatchAndSkipsExternalCall() {
        Batch batch = Batch.createDraft("label", "claude-3-5-sonnet-20240620");
        ReflectionTestUtils.setField(batch, "id", 11L);
        batch.submit("external-1");
        ReflectionTestUtils.setField(batch, "externalBatchId", "   ");

        when(batchRepository.findById(11L)).thenReturn(Optional.of(batch));

        var response = batchService.syncStatus(11L);

        assertThat(response.status()).isEqualTo(BatchStatus.FAILED);
        assertThat(response.errorMessage()).contains("External batch id is missing for batch: 11");
        verifyNoInteractions(batchApiClientFactory);
    }

    @Test
    void syncStatus_WhenCompleted_FetchesResultsAndCompletesBatch() {
        Batch batch = Batch.createDraft("label", "claude-3-5-sonnet-20240620");
        ReflectionTestUtils.setField(batch, "id", 1L);
        BatchPrompt prompt = BatchPrompt.create("prompt-1", "system", "user");
        ReflectionTestUtils.setField(prompt, "id", 101L);
        batch.addPrompt(prompt);
        batch.submit("ext-1");

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(batchApiClientFactory.getAdapter("claude-3-5-sonnet-20240620")).thenReturn(batchApiPort);
        when(batchApiPort.fetchStatus(any())).thenReturn(new org.jh.batchbridge.dto.external.BatchStatusResult(
            ExternalBatchStatus.COMPLETED, null));
        when(batchApiPort.fetchResults(any(), any())).thenReturn(java.util.Map.of(101L, new PromptResult(true, "result text", null)));

        batchService.syncStatus(1L);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(prompt.getStatus()).isEqualTo(org.jh.batchbridge.domain.PromptStatus.COMPLETED);
        assertThat(prompt.getResponseContent()).isEqualTo("result text");
    }

    @Test
    void syncStatus_WhenBatchNotFound_Throws() {
        when(batchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchService.syncStatus(99L))
                .isInstanceOf(BatchNotFoundException.class);
    }
}
