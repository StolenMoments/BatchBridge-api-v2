package org.jh.batchbridge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jh.batchbridge.dto.request.PromptTemplateCreateRequest;
import org.jh.batchbridge.dto.request.PromptTemplateUpdateRequest;
import org.jh.batchbridge.dto.response.PromptTemplateResponse;
import org.jh.batchbridge.exception.GlobalExceptionHandler;
import org.jh.batchbridge.exception.PromptTemplateNotFoundException;
import org.jh.batchbridge.service.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PromptTemplateController.class)
@Import(GlobalExceptionHandler.class)
class PromptTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PromptTemplateService promptTemplateService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getTemplates_Returns200() throws Exception {
        PromptTemplateResponse response = new PromptTemplateResponse(
                1L,
                "code-review-basic",
                "Java review template",
                "You are a senior engineer.",
                "Review the following code.",
                LocalDateTime.parse("2026-04-09T10:00:00"),
                LocalDateTime.parse("2026-04-09T10:00:00")
        );

        when(promptTemplateService.getAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].name").value("code-review-basic"));
    }

    @Test
    void getTemplate_Returns200() throws Exception {
        PromptTemplateResponse response = new PromptTemplateResponse(
                1L,
                "code-review-basic",
                "Java review template",
                "You are a senior engineer.",
                "Review the following code.",
                LocalDateTime.parse("2026-04-09T10:00:00"),
                LocalDateTime.parse("2026-04-09T10:00:00")
        );

        when(promptTemplateService.getOne(1L)).thenReturn(response);

        mockMvc.perform(get("/api/templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.userPrompt").value("Review the following code."));
    }

    @Test
    void createTemplate_Returns201() throws Exception {
        PromptTemplateCreateRequest request = new PromptTemplateCreateRequest(
                "code-review-basic",
                "Java review template",
                "You are a senior engineer.",
                "Review the following code."
        );
        PromptTemplateResponse response = new PromptTemplateResponse(
                1L,
                request.name(),
                request.description(),
                request.systemPrompt(),
                request.userPrompt(),
                LocalDateTime.parse("2026-04-09T10:00:00"),
                LocalDateTime.parse("2026-04-09T10:00:00")
        );

        when(promptTemplateService.create(any(PromptTemplateCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.name").value("code-review-basic"));
    }

    @Test
    void updateTemplate_Returns200() throws Exception {
        PromptTemplateUpdateRequest request = new PromptTemplateUpdateRequest(
                "review-updated",
                "updated description",
                "You are an expert reviewer.",
                "Apply the updated prompt."
        );
        PromptTemplateResponse response = new PromptTemplateResponse(
                1L,
                request.name(),
                request.description(),
                request.systemPrompt(),
                request.userPrompt(),
                LocalDateTime.parse("2026-04-09T10:00:00"),
                LocalDateTime.parse("2026-04-09T11:00:00")
        );

        when(promptTemplateService.update(eq(1L), any(PromptTemplateUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/templates/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("review-updated"))
                .andExpect(jsonPath("$.data.userPrompt").value("Apply the updated prompt."));
    }

    @Test
    void deleteTemplate_Returns204() throws Exception {
        doNothing().when(promptTemplateService).delete(1L);

        mockMvc.perform(delete("/api/templates/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getTemplate_NotFound_Returns404() throws Exception {
        when(promptTemplateService.getOne(999L)).thenThrow(new PromptTemplateNotFoundException(999L));

        mockMvc.perform(get("/api/templates/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TEMPLATE_NOT_FOUND"));
    }

    @Test
    void createTemplate_InvalidRequest_Returns400() throws Exception {
        PromptTemplateCreateRequest request = new PromptTemplateCreateRequest(
                "code-review-basic",
                "Java review template",
                "You are a senior engineer.",
                ""
        );

        mockMvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void createTemplate_NameTooLong_Returns400() throws Exception {
        PromptTemplateCreateRequest request = new PromptTemplateCreateRequest(
                repeat("a", 101),
                "Java review template",
                "You are a senior engineer.",
                "Review the following code."
        );

        mockMvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void updateTemplate_DescriptionTooLong_Returns400() throws Exception {
        PromptTemplateUpdateRequest request = new PromptTemplateUpdateRequest(
                "review-updated",
                repeat("d", 301),
                "You are an expert reviewer.",
                "Apply the updated prompt."
        );

        mockMvc.perform(put("/api/templates/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void deleteTemplate_NotFound_Returns404() throws Exception {
        doThrow(new PromptTemplateNotFoundException(999L)).when(promptTemplateService).delete(999L);

        mockMvc.perform(delete("/api/templates/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TEMPLATE_NOT_FOUND"));
    }

    private String repeat(String value, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> value)
                .collect(Collectors.joining());
    }
}
