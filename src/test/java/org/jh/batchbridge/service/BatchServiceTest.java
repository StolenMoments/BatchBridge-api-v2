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
import org.jh.batchbridge.dto.response.BatchDetailResponse;
import org.jh.batchbridge.dto.response.BatchListResponse;
import org.jh.batchbridge.dto.response.BatchSubmitResponse;
import org.jh.batchbridge.dto.response.BatchSyncPromptsResponse;
import org.jh.batchbridge.domain.PromptType;
import org.jh.batchbridge.exception.BatchEmptyException;
import org.jh.batchbridge.exception.BatchNotEditableException;
import org.jh.batchbridge.exception.BatchNotFoundException;
import org.jh.batchbridge.exception.BatchNotSyncedException;
import org.jh.batchbridge.exception.UnsupportedModelException;
import org.jh.batchbridge.exception.UnsupportedPromptTypeException;
import org.jh.batchbridge.domain.PromptStatus;
import java.util.Map;
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
        BatchCreateRequest.PromptPayload promptPayload = new BatchCreateRequest.PromptPayload("prompt-label", "system", "user");
        BatchCreateRequest request = new BatchCreateRequest("label", "unknown-model", promptPayload);
        when(batchApiClientFactory.getAdapter("unknown-model")).thenThrow(new UnsupportedModelException("unknown-model"));

        assertThatThrownBy(() -> batchService.createBatch(request))
                .isInstanceOf(UnsupportedModelException.class);

        verify(batchApiClientFactory).getAdapter("unknown-model");
        verifyNoInteractions(batchRepository);
    }

    @Test
    void createBatch_SavesBatchAndPromptTogether() {
        BatchCreateRequest.PromptPayload promptPayload = new BatchCreateRequest.PromptPayload("prompt-label", "system", "user");
        BatchCreateRequest request = new BatchCreateRequest("batch-label", "claude-3-5-sonnet-20240620", promptPayload);

        when(batchApiClientFactory.getAdapter("claude-3-5-sonnet-20240620")).thenReturn(batchApiPort);
        when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> {
            Batch b = invocation.getArgument(0);
            ReflectionTestUtils.setField(b, "id", 1L);
            if (!b.getPrompts().isEmpty()) {
                ReflectionTestUtils.setField(b.getPrompts().get(0), "id", 100L);
            }
            return b;
        });

        BatchDetailResponse response = batchService.createBatch(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.label()).isEqualTo("batch-label");
        assertThat(response.promptCount()).isEqualTo(1);
        assertThat(response.prompts()).hasSize(1);
        assertThat(response.prompts().get(0).userPrompt()).isEqualTo("user");
        assertThat(response.prompts().get(0).label()).isEqualTo("prompt-label");

        verify(batchRepository).save(argThat(batch ->
                batch.getLabel().equals("batch-label") &&
                        batch.getPrompts().size() == 1 &&
                        batch.getPrompts().get(0).getUserPrompt().equals("user")
        ));
    }

    @Test
    void createBatch_DefaultPromptLabel_WhenPromptLabelIsMissing() {
        BatchCreateRequest.PromptPayload promptPayload = new BatchCreateRequest.PromptPayload(null, "system", "user");
        BatchCreateRequest request = new BatchCreateRequest("batch-label", "claude-3-5-sonnet-20240620", promptPayload);

        when(batchApiClientFactory.getAdapter("claude-3-5-sonnet-20240620")).thenReturn(batchApiPort);
        when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BatchDetailResponse response = batchService.createBatch(request);

        assertThat(response.prompts().get(0).label()).isEqualTo("Prompt 1");
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
    void getDetail_FiltersDeletedBatch() {
        Batch batch = Batch.createDraft("label", "claude");
        ReflectionTestUtils.setField(batch, "id", 1L);
        batch.delete();

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.getDetail(1L))
                .isInstanceOf(BatchNotFoundException.class);
    }

    @Test
    void deleteBatch_Success_WhenDraft() {
        Batch batch = Batch.createDraft("label", "claude");
        ReflectionTestUtils.setField(batch, "id", 1L);

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        batchService.deleteBatch(1L);

        assertThat(batch.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteBatch_ThrowsNotEditable_WhenInProgress() {
        Batch batch = Batch.createDraft("label", "claude");
        ReflectionTestUtils.setField(batch, "id", 1L);
        batch.submit("ext-1");

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.deleteBatch(1L))
                .isInstanceOf(BatchNotEditableException.class)
                .hasMessage("Only DRAFT batches can be deleted.");
    }

    @Test
    void submitBatch_ThrowsNotFound_WhenBatchIsDeleted() {
        Batch batch = Batch.createDraft("label", "claude-3-5-sonnet-20240620");
        ReflectionTestUtils.setField(batch, "id", 1L);
        batch.delete();

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.submitBatch(1L))
                .isInstanceOf(BatchNotFoundException.class);

        verifyNoInteractions(batchApiClientFactory);
    }

    @Test
    void syncStatus_ThrowsNotFound_WhenBatchIsDeleted() {
        Batch batch = Batch.createDraft("label", "claude-3-5-sonnet-20240620");
        ReflectionTestUtils.setField(batch, "id", 1L);
        batch.delete();

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.syncStatus(1L))
                .isInstanceOf(BatchNotFoundException.class);

        verifyNoInteractions(batchApiClientFactory);
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
        when(batchApiPort.supportsPromptType(any())).thenReturn(true);
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
    void submitBatch_WhenPromptTypeUnsupported_ThrowsUnsupportedPromptType() {
        Batch batch = Batch.createDraft("label", "claude-3-5-sonnet-20240620");
        ReflectionTestUtils.setField(batch, "id", 1L);
        BatchPrompt prompt = BatchPrompt.create("prompt-1", null, "draw a cat", PromptType.IMAGE_GENERATION, null);
        ReflectionTestUtils.setField(prompt, "id", 101L);
        batch.addPrompt(prompt);

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(batchApiClientFactory.getAdapter("claude-3-5-sonnet-20240620")).thenReturn(batchApiPort);
        when(batchApiPort.supportsPromptType(PromptType.IMAGE_GENERATION)).thenReturn(false);

        assertThatThrownBy(() -> batchService.submitBatch(1L))
                .isInstanceOf(UnsupportedPromptTypeException.class)
                .hasMessageContaining("IMAGE_GENERATION");
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
        when(batchApiPort.fetchResults(any(), any())).thenReturn(java.util.Map.of(101L, new PromptResult(true, "result text", null, null)));

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

    @Test
    void syncPrompts_WhenBatchNotCompleted_ThrowsBatchNotSynced() {
        Batch batch = Batch.builder().id(1L).status(BatchStatus.IN_PROGRESS).build();
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.syncPrompts(1L))
                .isInstanceOf(BatchNotSyncedException.class)
                .hasMessageContaining("Only COMPLETED batches");
    }

    @Test
    void updateBatch_UpdatesLabel_WhenOnlyLabelProvided() {
        Batch batch = Batch.createDraft("old-label", "claude-3-5-sonnet-20240620");
        ReflectionTestUtils.setField(batch, "id", 1L);
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        // model이 null이므로 getAdapter 호출 없음

        BatchDetailResponse response = batchService.updateBatch(1L, new org.jh.batchbridge.dto.request.BatchUpdateRequest("new-label", null));

        assertThat(response.label()).isEqualTo("new-label");
    }

    @Test
    void updateBatch_UpdatesModel_WhenOnlyModelProvided() {
        Batch batch = Batch.createDraft("label", "claude-old");
        ReflectionTestUtils.setField(batch, "id", 1L);
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(batchApiClientFactory.getAdapter("claude-new")).thenReturn(batchApiPort);

        BatchDetailResponse response = batchService.updateBatch(1L, new org.jh.batchbridge.dto.request.BatchUpdateRequest(null, "claude-new"));

        assertThat(response.model()).isEqualTo("claude-new");
    }

    @Test
    void updateBatch_ThrowsIllegalArgument_WhenBothNull() {
        assertThatThrownBy(() -> batchService.updateBatch(1L, new org.jh.batchbridge.dto.request.BatchUpdateRequest(null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
    }

    @Test
    void updateBatch_ThrowsNotFound_WhenDeleted() {
        Batch batch = Batch.createDraft("label", "claude");
        ReflectionTestUtils.setField(batch, "id", 1L);
        batch.delete();
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.updateBatch(1L, new org.jh.batchbridge.dto.request.BatchUpdateRequest("new", null)))
                .isInstanceOf(BatchNotFoundException.class);
    }

    @Test
    void updateBatch_ThrowsNotEditable_WhenNotDraft() {
        Batch batch = Batch.createDraft("label", "claude");
        ReflectionTestUtils.setField(batch, "id", 1L);
        batch.submit("ext-1");
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.updateBatch(1L, new org.jh.batchbridge.dto.request.BatchUpdateRequest("new", null)))
                .isInstanceOf(BatchNotEditableException.class);
    }

    @Test
    void updateBatch_ThrowsUnsupportedModel_WhenModelNotSupported() {
        Batch batch = Batch.createDraft("label", "claude");
        ReflectionTestUtils.setField(batch, "id", 1L);
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(batchApiClientFactory.getAdapter("gpt-4")).thenThrow(new UnsupportedModelException("gpt-4"));

        assertThatThrownBy(() -> batchService.updateBatch(1L, new org.jh.batchbridge.dto.request.BatchUpdateRequest(null, "gpt-4")))
                .isInstanceOf(UnsupportedModelException.class);
    }

    @Test
    void syncStatus_WhenNotInProgress_ReturnsCurrentStatus() {
        Batch batch = Batch.createDraft("label", "claude-3-5-sonnet-20240620");
        ReflectionTestUtils.setField(batch, "id", 1L);
        // DRAFT 상태 — IN_PROGRESS가 아니므로 외부 호출 없이 즉시 반환
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        BatchDetailResponse response = batchService.syncStatus(1L);

        assertThat(response.status()).isEqualTo(BatchStatus.DRAFT);
        verifyNoInteractions(batchApiClientFactory);
    }

    @Test
    void syncStatus_WhenFailed_FailsBatch() {
        Batch batch = Batch.createDraft("label", "claude-3-5-sonnet-20240620");
        ReflectionTestUtils.setField(batch, "id", 1L);
        batch.submit("ext-1");
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(batchApiClientFactory.getAdapter("claude-3-5-sonnet-20240620")).thenReturn(batchApiPort);
        when(batchApiPort.fetchStatus(any())).thenReturn(
                new org.jh.batchbridge.dto.external.BatchStatusResult(
                        org.jh.batchbridge.dto.external.ExternalBatchStatus.FAILED, "external error"));

        batchService.syncStatus(1L);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(batch.getErrorMessage()).isEqualTo("external error");
    }

    @Test
    void syncStatus_WhenFailed_WithNullErrorMessage_UsesDefaultMessage() {
        Batch batch = Batch.createDraft("label", "claude-3-5-sonnet-20240620");
        ReflectionTestUtils.setField(batch, "id", 1L);
        batch.submit("ext-1");
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(batchApiClientFactory.getAdapter("claude-3-5-sonnet-20240620")).thenReturn(batchApiPort);
        when(batchApiPort.fetchStatus(any())).thenReturn(
                new org.jh.batchbridge.dto.external.BatchStatusResult(
                        org.jh.batchbridge.dto.external.ExternalBatchStatus.FAILED, null));

        batchService.syncStatus(1L);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(batch.getErrorMessage()).isEqualTo("External batch processing failed");
    }

    @Test
    void syncPrompts_WhenNoTargetPrompts_ReturnsZero() {
        Batch batch = Batch.builder()
                .id(1L)
                .status(BatchStatus.COMPLETED)
                .model("claude-model")
                .externalBatchId("ext-id")
                .build();
        BatchPrompt completedPrompt = BatchPrompt.builder().id(101L).status(PromptStatus.COMPLETED).responseContent("ok").build();
        batch.addPrompt(completedPrompt);

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        BatchSyncPromptsResponse response = batchService.syncPrompts(1L);

        assertThat(response.resynced()).isEqualTo(0);
        assertThat(response.stillFailed()).isEqualTo(0);
        verifyNoInteractions(batchApiClientFactory);
    }

    @Test
    void syncPrompts_WhenSomePromptsFailed_ResyncsThem() {
        Batch batch = Batch.builder()
                .id(1L)
                .status(BatchStatus.COMPLETED)
                .model("claude-model")
                .externalBatchId("ext-id")
                .build();
        BatchPrompt p1 = BatchPrompt.builder().id(101L).status(PromptStatus.COMPLETED).responseContent("already-ok").build();
        BatchPrompt p2 = BatchPrompt.builder().id(102L).status(PromptStatus.FAILED).errorMessage("old-error").build();
        BatchPrompt p3 = BatchPrompt.builder().id(103L).status(PromptStatus.PENDING).build();
        batch.addPrompt(p1);
        batch.addPrompt(p2);
        batch.addPrompt(p3);

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(batchApiClientFactory.getAdapter("claude-model")).thenReturn(batchApiPort);
        
        // p2, p3 should be targets
        Map<Long, PromptResult> results = Map.of(
                102L, new PromptResult(true, "new-content", null, null),
                103L, new PromptResult(false, null, "still-no-result", null)
        );
        when(batchApiPort.fetchResults(any(), any())).thenReturn(results);

        BatchSyncPromptsResponse response = batchService.syncPrompts(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.resynced()).isEqualTo(1); // p2
        assertThat(response.stillFailed()).isEqualTo(1); // p3

        assertThat(p1.getStatus()).isEqualTo(PromptStatus.COMPLETED);
        assertThat(p1.getResponseContent()).isEqualTo("already-ok");
        
        assertThat(p2.getStatus()).isEqualTo(PromptStatus.COMPLETED);
        assertThat(p2.getResponseContent()).isEqualTo("new-content");
        
        assertThat(p3.getStatus()).isEqualTo(PromptStatus.FAILED);
        assertThat(p3.getErrorMessage()).isEqualTo("still-no-result");
    }
}
