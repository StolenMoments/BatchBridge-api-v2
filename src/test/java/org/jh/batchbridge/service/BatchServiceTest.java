package org.jh.batchbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jh.batchbridge.domain.Batch;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.dto.request.BatchCreateRequest;
import org.jh.batchbridge.dto.response.BatchListResponse;
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

        when(batchRepository.findBatchSummaries(any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        BatchListResponse response = batchService.getList(null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).promptCount()).isEqualTo(3);
        verify(batchRepository).findBatchSummaries(any(), any());
    }

    @Test
    void syncStatus_MissingExternalBatchId_FailsBatchAndSkipsExternalCall() {
        Batch batch = new Batch("label", "claude-3-5-sonnet-20240620");
        ReflectionTestUtils.setField(batch, "id", 11L);
        batch.submit("external-1");
        batch.setExternalBatchId("   ");

        when(batchRepository.findById(11L)).thenReturn(Optional.of(batch));

        var response = batchService.syncStatus(11L);

        assertThat(response.status()).isEqualTo(BatchStatus.FAILED);
        assertThat(response.errorMessage()).contains("External batch id is missing for batch: 11");
        verifyNoInteractions(batchApiClientFactory);
    }

    @Test
    void syncStatus_WhenBatchNotFound_Throws() {
        when(batchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchService.syncStatus(99L))
                .isInstanceOf(BatchNotFoundException.class);
    }
}
