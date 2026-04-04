package org.jh.batchbridge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jh.batchbridge.dto.request.external.ContextPreviewRequest;
import org.jh.batchbridge.dto.response.external.ContextPreviewResponse;
import org.jh.batchbridge.dto.response.external.SourceResult;
import org.jh.batchbridge.dto.response.external.SourceStatus;
import org.jh.batchbridge.dto.response.external.SourceType;
import org.jh.batchbridge.exception.ExternalApiException;
import org.jh.batchbridge.service.ExternalContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(ExternalContextController.class)
class ExternalContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExternalContextService externalContextService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void preview_AllSuccess_Returns200WithToastSuccess() throws Exception {
        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/org/repo/pull/1",
                List.of("DEV-58"),
                null
        );

        List<SourceResult> sources = List.of(
                new SourceResult(SourceType.GITHUB_PR, "1", "fix: bug", SourceStatus.SUCCESS, "[GitHub PR] #1: fix: bug", null),
                new SourceResult(SourceType.JIRA, "DEV-58", "Some task", SourceStatus.SUCCESS, "[Jira] DEV-58: Some task", null)
        );
        ContextPreviewResponse response = new ContextPreviewResponse(sources, null);

        when(externalContextService.preview(any(ContextPreviewRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/context/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sources[0].type").value("GITHUB_PR"))
                .andExpect(jsonPath("$.data.sources[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.sources[0].formattedText").value("[GitHub PR] #1: fix: bug"))
                .andExpect(jsonPath("$.data.sources[1].type").value("JIRA"))
                .andExpect(jsonPath("$.data.sources[1].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.sources[1].formattedText").value("[Jira] DEV-58: Some task"))
                .andExpect(jsonPath("$.data.toast.title").isNotEmpty())
                .andExpect(jsonPath("$.data.toast.description").isNotEmpty());
    }

    @Test
    void preview_PartialFailure_Returns200WithToastPartial() throws Exception {
        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/org/repo/pull/1",
                null,
                List.of("491521")
        );

        List<SourceResult> sources = List.of(
                new SourceResult(SourceType.GITHUB_PR, "1", "fix: bug", SourceStatus.SUCCESS, "[GitHub PR] #1: fix: bug", null),
                new SourceResult(SourceType.CONFLUENCE, "491521", null, SourceStatus.FAILED, null, "Not found")
        );
        ContextPreviewResponse response = new ContextPreviewResponse(sources, null);

        when(externalContextService.preview(any(ContextPreviewRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/context/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sources[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data.sources[1].formattedText").doesNotExist())
                .andExpect(jsonPath("$.data.sources[1].error").value("Not found"))
                .andExpect(jsonPath("$.data.toast.title").isNotEmpty());
    }

    @Test
    void preview_AllSourcesFailed_Returns502() throws Exception {
        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/org/repo/pull/1", null, null
        );

        when(externalContextService.preview(any(ContextPreviewRequest.class)))
                .thenThrow(new ExternalApiException("All context sources failed to fetch."));

        mockMvc.perform(post("/api/context/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONTEXT_FETCH_FAILED"));
    }

    @Test
    void preview_NoSourceProvided_Returns400() throws Exception {
        ContextPreviewRequest request = new ContextPreviewRequest(null, null, null);

        when(externalContextService.preview(any(ContextPreviewRequest.class)))
                .thenThrow(new IllegalArgumentException("At least one source must be provided."));

        mockMvc.perform(post("/api/context/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
