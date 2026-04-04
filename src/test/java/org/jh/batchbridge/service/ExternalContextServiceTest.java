package org.jh.batchbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jh.batchbridge.dto.request.external.ContextPreviewRequest;
import org.jh.batchbridge.dto.response.external.ContextPreviewResponse;
import org.jh.batchbridge.dto.response.external.SourceStatus;
import org.jh.batchbridge.dto.response.external.SourceType;
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

    // -------------------------------------------------------------------------
    // GitHub PR
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void preview_GithubPr_ReturnsPrTitleAndDiff() {
        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/owner/repo/pull/42", null, null);

        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        // Both the PR detail call and the files call go through the same mock chain.
        // body(Map.class) returns PR detail; body(List.class) returns files list.
        when(githubRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString(), anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "title", "fix: critical bug",
                "body", "This PR fixes a critical bug."
        ));
        when(responseSpec.body(List.class)).thenReturn(List.of(
                Map.of("filename", "Foo.java", "additions", 5, "deletions", 2, "patch", "@@ -1 +1 @@\n+fix")
        ));

        ContextPreviewResponse response = externalContextService.preview(request);

        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).type()).isEqualTo(SourceType.GITHUB_PR);
        assertThat(response.sources().get(0).id()).isEqualTo("42");
        assertThat(response.sources().get(0).title()).isEqualTo("fix: critical bug");
        assertThat(response.sources().get(0).status()).isEqualTo(SourceStatus.SUCCESS);
        assertThat(response.sources().get(0).formattedText()).contains("[GitHub PR] #42: fix: critical bug");
        assertThat(response.sources().get(0).formattedText()).contains("## Changed Files");
    }

    @Test
    void preview_GithubPr_InvalidUrl_ReturnsFailedSource() {
        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/owner/repo", List.of("DEV-1"), null);

        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "fields", Map.of("summary", "Test issue",
                        "status", Map.of("name", "In Progress"),
                        "issuetype", Map.of("name", "Story"))
        ));

        ContextPreviewResponse response = externalContextService.preview(request);

        assertThat(response.sources()).hasSize(2);
        assertThat(response.sources().get(0).status()).isEqualTo(SourceStatus.FAILED);
        assertThat(response.sources().get(0).formattedText()).isNull();
        assertThat(response.sources().get(0).error()).contains("Invalid GitHub PR URL format");
        assertThat(response.sources().get(1).status()).isEqualTo(SourceStatus.SUCCESS);
    }

    @Test
    void preview_GithubClientNull_ReturnsFailedSource() {
        ExternalContextService serviceWithoutGithub = new ExternalContextService(null, atlassianRestClient);

        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);
        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "fields", Map.of("summary", "Test",
                        "status", Map.of("name", "To Do"),
                        "issuetype", Map.of("name", "Bug"))
        ));

        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/owner/repo/pull/1", List.of("DEV-1"), null);
        ContextPreviewResponse response = serviceWithoutGithub.preview(request);

        assertThat(response.sources().get(0).status()).isEqualTo(SourceStatus.FAILED);
        assertThat(response.sources().get(0).formattedText()).isNull();
        assertThat(response.sources().get(0).error()).contains("GitHub client is not configured");
        assertThat(response.sources().get(1).status()).isEqualTo(SourceStatus.SUCCESS);
    }

    // -------------------------------------------------------------------------
    // Jira
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void preview_Jira_ReturnsSummaryStatusType() {
        ContextPreviewRequest request = new ContextPreviewRequest(null, List.of("DEV-123"), null);

        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "fields", Map.of(
                        "summary", "Implement feature X",
                        "status", Map.of("name", "In Progress"),
                        "issuetype", Map.of("name", "Story"),
                        "description", Map.of(
                                "type", "doc",
                                "content", List.of(
                                        Map.of("type", "paragraph",
                                                "content", List.of(Map.of("type", "text", "text", "Feature description")))
                                )
                        )
                )
        ));

        ContextPreviewResponse response = externalContextService.preview(request);

        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).type()).isEqualTo(SourceType.JIRA);
        assertThat(response.sources().get(0).id()).isEqualTo("DEV-123");
        assertThat(response.sources().get(0).title()).isEqualTo("Implement feature X");
        assertThat(response.sources().get(0).status()).isEqualTo(SourceStatus.SUCCESS);
        assertThat(response.sources().get(0).formattedText()).contains("[Jira] DEV-123: Implement feature X");
        assertThat(response.sources().get(0).formattedText()).contains("Status: In Progress");
        assertThat(response.sources().get(0).formattedText()).contains("Type: Story");
        assertThat(response.sources().get(0).formattedText()).contains("Feature description");
    }

    @Test
    void preview_AtlassianClientNull_ReturnsFailedSource() {
        ExternalContextService serviceWithoutAtlassian = new ExternalContextService(null, null);

        ContextPreviewRequest request = new ContextPreviewRequest(null, List.of("DEV-1"), null);

        assertThatThrownBy(() -> serviceWithoutAtlassian.preview(request))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("All context sources failed");
    }

    // -------------------------------------------------------------------------
    // Confluence
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void preview_Confluence_ReturnsPlainText() {
        ContextPreviewRequest request = new ContextPreviewRequest(null, null, List.of("491521"));

        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "title", "Architecture Overview",
                "body", Map.of("storage", Map.of("value", "<p>This is the <strong>architecture</strong> overview.</p>"))
        ));

        ContextPreviewResponse response = externalContextService.preview(request);

        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).type()).isEqualTo(SourceType.CONFLUENCE);
        assertThat(response.sources().get(0).title()).isEqualTo("Architecture Overview");
        assertThat(response.sources().get(0).status()).isEqualTo(SourceStatus.SUCCESS);
        assertThat(response.sources().get(0).formattedText()).contains("[Confluence] Architecture Overview");
        assertThat(response.sources().get(0).formattedText()).contains("This is the architecture overview.");
    }

    // -------------------------------------------------------------------------
    // Partial success / all failed
    // -------------------------------------------------------------------------

    @Test
    void preview_AllFailed_ThrowsExternalApiException() {
        ExternalContextService serviceWithoutClients = new ExternalContextService(null, null);

        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/owner/repo/pull/1",
                List.of("DEV-1"),
                List.of("491521")
        );

        assertThatThrownBy(() -> serviceWithoutClients.preview(request))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("All context sources failed");
    }

    @Test
    void preview_NoSourceProvided_ThrowsIllegalArgumentException() {
        ContextPreviewRequest request = new ContextPreviewRequest(null, null, null);

        assertThatThrownBy(() -> externalContextService.preview(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one source");
    }

}
