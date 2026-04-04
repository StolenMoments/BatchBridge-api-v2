package org.jh.batchbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import org.jh.batchbridge.dto.request.external.GithubPreviewRequest;
import org.jh.batchbridge.dto.request.external.JiraPreviewRequest;
import org.jh.batchbridge.dto.response.external.ExternalContextPreviewResponse;
import org.jh.batchbridge.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;

@ExtendWith(MockitoExtension.class)
class ExternalContextServiceTest {

    @Mock
    private RestClient githubRestClient;

    @Mock
    private RestClient atlassianRestClient;

    private ExternalContextService externalContextService;

    @BeforeEach
    void setUp() {
        externalContextService = new ExternalContextService(githubRestClient, atlassianRestClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewGithub_ReturnsDecodedContent() {
        GithubPreviewRequest request = new GithubPreviewRequest("owner", "repo", "path.txt", "main");
        
        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(githubRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        
        String base64Content = Base64.getEncoder().encodeToString("hello world".getBytes());
        when(responseSpec.body(Map.class)).thenReturn(Map.of("content", base64Content));

        ExternalContextPreviewResponse response = externalContextService.previewGithub(request);

        assertThat(response.label()).isEqualTo("GitHub: owner/repo/path.txt");
        assertThat(response.content()).isEqualTo("hello world");
    }

    @Test
    void previewGithub_WhenClientNull_ThrowsException() {
        ExternalContextService service = new ExternalContextService(null, null);
        GithubPreviewRequest request = new GithubPreviewRequest("owner", "repo", "path.txt", null);

        assertThatThrownBy(() -> service.previewGithub(request))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("GitHub client is not configured");
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewJira_ReturnsIssueSummary() {
        JiraPreviewRequest request = new JiraPreviewRequest("DEV-123");

        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> mockResponse = Map.of(
                "fields", Map.of(
                        "summary", "Test summary",
                        "description", Map.of("type", "doc")
                )
        );
        when(responseSpec.body(Map.class)).thenReturn(mockResponse);

        ExternalContextPreviewResponse response = externalContextService.previewJira(request);

        assertThat(response.label()).isEqualTo("JIRA: DEV-123");
        assertThat(response.content()).contains("Summary: Test summary");
        assertThat(response.content()).contains("Issue Key: DEV-123");
    }

    @Test
    void previewJira_WhenClientNull_ThrowsException() {
        ExternalContextService service = new ExternalContextService(null, null);
        JiraPreviewRequest request = new JiraPreviewRequest("DEV-123");

        assertThatThrownBy(() -> service.previewJira(request))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Atlassian client is not configured");
    }
}
