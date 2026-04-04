package org.jh.batchbridge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jh.batchbridge.dto.request.external.GithubPreviewRequest;
import org.jh.batchbridge.dto.request.external.JiraPreviewRequest;
import org.jh.batchbridge.dto.response.external.ExternalContextPreviewResponse;
import org.jh.batchbridge.service.ExternalContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExternalContextController.class)
class ExternalContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExternalContextService externalContextService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void previewGithub_ReturnsSuccess() throws Exception {
        GithubPreviewRequest request = new GithubPreviewRequest("owner", "repo", "path", null);
        ExternalContextPreviewResponse response = new ExternalContextPreviewResponse("label", "content");

        when(externalContextService.previewGithub(any(GithubPreviewRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/external-context/preview/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.label").value("label"))
                .andExpect(jsonPath("$.data.content").value("content"));
    }

    @Test
    void previewJira_ReturnsSuccess() throws Exception {
        JiraPreviewRequest request = new JiraPreviewRequest("DEV-123");
        ExternalContextPreviewResponse response = new ExternalContextPreviewResponse("label", "content");

        when(externalContextService.previewJira(any(JiraPreviewRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/external-context/preview/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.label").value("label"))
                .andExpect(jsonPath("$.data.content").value("content"));
    }

    @Test
    void previewGithub_ValidationFailure() throws Exception {
        GithubPreviewRequest request = new GithubPreviewRequest("", "", "", null);

        mockMvc.perform(post("/api/external-context/preview/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
