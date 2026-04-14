package org.jh.batchbridge.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jh.batchbridge.domain.PromptStatus;
import org.jh.batchbridge.domain.PromptType;
import org.jh.batchbridge.dto.request.PromptAddRequest;
import org.jh.batchbridge.dto.request.PromptUpdateRequest;
import org.jh.batchbridge.dto.response.BatchPromptResponse;
import org.jh.batchbridge.exception.GlobalExceptionHandler;
import org.jh.batchbridge.service.PromptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PromptController.class)
@Import({GlobalExceptionHandler.class})
class PromptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PromptService promptService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void addPrompt_Returns201() throws Exception {
        PromptAddRequest request = new PromptAddRequest("label", "system", "user");
        BatchPromptResponse response = new BatchPromptResponse(1L, "label", "system", "user", PromptStatus.PENDING, null, null);

        when(promptService.addPrompt(eq(1L), any(PromptAddRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/batches/1/prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.label").value("label"));
    }

    @Test
    void updatePrompt_Returns200() throws Exception {
        PromptUpdateRequest request = new PromptUpdateRequest("updated-label", null, "updated-user", null, null, null, null);
        BatchPromptResponse response = new BatchPromptResponse(10L, "updated-label", "system", "updated-user", PromptStatus.PENDING, null, null);

        when(promptService.updatePrompt(eq(1L), eq(10L), any(PromptUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/batches/1/prompts/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.label").value("updated-label"))
                .andExpect(jsonPath("$.data.userPrompt").value("updated-user"));
    }

    @Test
    void deletePrompt_Returns204() throws Exception {
        doNothing().when(promptService).deletePrompt(1L, 10L);

        mockMvc.perform(delete("/api/batches/1/prompts/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getPrompt_Returns200() throws Exception {
        BatchPromptResponse response = new BatchPromptResponse(10L, "label", "system", "user", PromptStatus.PENDING, null, null);

        when(promptService.getPrompt(1L, 10L)).thenReturn(response);

        mockMvc.perform(get("/api/batches/1/prompts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10L))
                .andExpect(jsonPath("$.data.label").value("label"));
    }

    @Test
    void addPrompt_InvalidRequest_Returns400() throws Exception {
        PromptAddRequest request = new PromptAddRequest("label", "system", ""); // empty userPrompt

        mockMvc.perform(post("/api/batches/1/prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void addPrompt_WithPromptType_ReturnsPromptType() throws Exception {
        PromptAddRequest request = new PromptAddRequest("label", "system", "user", PromptType.IMAGE_GENERATION, null, null, null);
        BatchPromptResponse response = new BatchPromptResponse(1L, "label", "system", "user", PromptStatus.PENDING,
                PromptType.IMAGE_GENERATION, null, null, null, null, java.util.List.of());

        when(promptService.addPrompt(eq(1L), any(PromptAddRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/batches/1/prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.promptType").value("IMAGE_GENERATION"));
    }

    @Test
    void addPrompt_WithoutPromptType_PassesNullToService() throws Exception {
        PromptAddRequest request = new PromptAddRequest("label", "system", "user");
        BatchPromptResponse response = new BatchPromptResponse(1L, "label", "system", "user", PromptStatus.PENDING, null, null);

        when(promptService.addPrompt(eq(1L), any(PromptAddRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/batches/1/prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        ArgumentCaptor<PromptAddRequest> captor = ArgumentCaptor.forClass(PromptAddRequest.class);
        verify(promptService).addPrompt(eq(1L), captor.capture());
        assertThat(captor.getValue().promptType()).isNull();
    }

    @Test
    void getPrompt_WithResultMedia_ReturnsMediaUrl() throws Exception {
        BatchPromptResponse response = new BatchPromptResponse(10L, "label", "system", "user", PromptStatus.COMPLETED,
                PromptType.IMAGE_GENERATION, null, "/api/media/1/10", null, null, java.util.List.of());

        when(promptService.getPrompt(1L, 10L)).thenReturn(response);

        mockMvc.perform(get("/api/batches/1/prompts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultMediaUrl").value("/api/media/1/10"));
    }
}
