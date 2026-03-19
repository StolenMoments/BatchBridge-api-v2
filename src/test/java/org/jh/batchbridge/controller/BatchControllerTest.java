package org.jh.batchbridge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.jh.batchbridge.domain.BatchStatus;
import org.jh.batchbridge.dto.request.BatchCreateRequest;
import org.jh.batchbridge.dto.response.BatchDetailResponse;
import org.jh.batchbridge.dto.response.BatchListResponse;
import org.jh.batchbridge.dto.response.BatchPromptResponse;
import org.jh.batchbridge.dto.response.BatchSummaryResponse;
import org.jh.batchbridge.domain.PromptStatus;
import org.jh.batchbridge.exception.GlobalExceptionHandler;
import org.jh.batchbridge.service.BatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BatchController.class)
@Import({GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "batch-bridge.supported-models[0].id=claude-3-5-sonnet-20240620",
        "batch-bridge.supported-models[0].label=Claude 3.5 Sonnet"
})
class BatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BatchService batchService;

    @Test
    void createBatch_Returns201() throws Exception {
        BatchCreateRequest request = new BatchCreateRequest("test-label", "claude-3-5-sonnet-20240620", "system", "user");
        BatchPromptResponse prompt = new BatchPromptResponse(1L, "test-label-prompt-1", "system", "user", PromptStatus.PENDING, null, null);
        BatchDetailResponse response = new BatchDetailResponse(
                1L, "test-label", "claude-3-5-sonnet-20240620", BatchStatus.IN_PROGRESS,
                List.of(prompt), null, LocalDateTime.now(), null
        );

        when(batchService.createBatch(any(BatchCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void getList_Returns200() throws Exception {
        BatchSummaryResponse summary = new BatchSummaryResponse(
                1L, "label", "model", BatchStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now()
        );
        BatchListResponse response = new BatchListResponse(List.of(summary), 1L, 1, 0, 20);

        when(batchService.getList(eq(BatchStatus.COMPLETED), anyInt(), anyInt())).thenReturn(response);

        mockMvc.perform(get("/api/batches")
                        .param("status", "COMPLETED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1L))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getList_ReturnsEmptyContent() throws Exception {
        BatchListResponse response = new BatchListResponse(List.of(), 0L, 0, 0, 20);

        when(batchService.getList(null, 0, 20)).thenReturn(response);

        mockMvc.perform(get("/api/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void getList_InvalidStatus_Returns400() throws Exception {
        mockMvc.perform(get("/api/batches")
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(batchService);
    }

    @Test
    void getDetail_Returns200() throws Exception {
        BatchPromptResponse prompt = new BatchPromptResponse(1L, "label-prompt-1", "system", "user", PromptStatus.COMPLETED, "result", null);
        BatchDetailResponse response = new BatchDetailResponse(
                1L, "label", "model", BatchStatus.COMPLETED,
                List.of(prompt), null, LocalDateTime.now(), LocalDateTime.now()
        );

        when(batchService.getDetail(1L)).thenReturn(response);

        mockMvc.perform(get("/api/batches/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.prompts[0].responseContent").value("result"));
    }

    @Test
    void syncStatus_Returns200() throws Exception {
        BatchPromptResponse prompt = new BatchPromptResponse(1L, "label-prompt-1", "system", "user", PromptStatus.COMPLETED, "result", null);
        BatchDetailResponse response = new BatchDetailResponse(
                1L, "label", "model", BatchStatus.COMPLETED,
                List.of(prompt), null, LocalDateTime.now(), LocalDateTime.now()
        );

        when(batchService.syncStatus(1L)).thenReturn(response);

        mockMvc.perform(post("/api/batches/1/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void getModels_ReturnsSupportedModels() throws Exception {
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].label").exists());
    }
}
