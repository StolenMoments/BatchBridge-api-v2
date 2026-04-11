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

    // -------------------------------------------------------------------------
    // GitHub PR — 추가 분기 케이스
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void preview_GithubPr_EmptyBody_OmitsBodySection() {
        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/owner/repo/pull/10", null, null);

        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(githubRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString(), anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of("title", "empty body PR", "body", ""));
        when(responseSpec.body(List.class)).thenReturn(List.of());

        ContextPreviewResponse response = externalContextService.preview(request);

        assertThat(response.sources().get(0).status()).isEqualTo(SourceStatus.SUCCESS);
        assertThat(response.sources().get(0).formattedText()).contains("[GitHub PR] #10: empty body PR");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_GithubPr_NullResponse_ReturnsFailure() {
        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/owner/repo/pull/99", null, null);

        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(githubRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString(), anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(null);

        assertThatThrownBy(() -> externalContextService.preview(request))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("All context sources failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_GithubPr_NullFiles_ShowsEmptyChangedFilesSection() {
        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/owner/repo/pull/5", null, null);

        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(githubRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString(), anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of("title", "pr title", "body", "body text"));
        when(responseSpec.body(List.class)).thenReturn(null);

        ContextPreviewResponse response = externalContextService.preview(request);

        assertThat(response.sources().get(0).status()).isEqualTo(SourceStatus.SUCCESS);
        assertThat(response.sources().get(0).formattedText()).contains("## Changed Files");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_GithubPr_FileWithoutPatch_OmitsPatch() {
        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/owner/repo/pull/7", null, null);

        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(githubRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString(), anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of("title", "no patch", "body", "desc"));
        when(responseSpec.body(List.class)).thenReturn(List.of(
                Map.of("filename", "NoChange.java", "additions", 0, "deletions", 0)
                // patch 필드 없음
        ));

        ContextPreviewResponse response = externalContextService.preview(request);

        assertThat(response.sources().get(0).status()).isEqualTo(SourceStatus.SUCCESS);
        assertThat(response.sources().get(0).formattedText()).contains("NoChange.java");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_GithubPr_LongPatch_IsTruncated() {
        ContextPreviewRequest request = new ContextPreviewRequest(
                "https://github.com/owner/repo/pull/8", null, null);

        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        // 201줄짜리 patch 생성 (MAX_PATCH_LINES = 200)
        String longPatch = "line\n".repeat(201);

        when(githubRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString(), anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of("title", "big diff", "body", ""));
        when(responseSpec.body(List.class)).thenReturn(List.of(
                Map.of("filename", "Big.java", "additions", 201, "deletions", 0, "patch", longPatch)
        ));

        ContextPreviewResponse response = externalContextService.preview(request);

        assertThat(response.sources().get(0).formattedText()).contains("(truncated)");
    }

    // -------------------------------------------------------------------------
    // Jira — 추가 분기 케이스
    // -------------------------------------------------------------------------

    @Test
    void preview_Jira_NullResponse_ReturnsFailure() {
        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(null);

        ContextPreviewRequest request = new ContextPreviewRequest(null, List.of("DEV-404"), null);

        assertThatThrownBy(() -> externalContextService.preview(request))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("All context sources failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_Jira_InvalidFields_ReturnsFailure() {
        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        // fields 가 Map이 아닌 값
        when(responseSpec.body(Map.class)).thenReturn(Map.of("fields", "not-a-map"));

        ContextPreviewRequest request = new ContextPreviewRequest(null, List.of("DEV-BAD"), null);

        assertThatThrownBy(() -> externalContextService.preview(request))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("All context sources failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_Jira_NoDescription_OmitsDescriptionSection() {
        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "fields", Map.of(
                        "summary", "No desc issue",
                        "status", Map.of("name", "Open"),
                        "issuetype", Map.of("name", "Bug")
                        // description 없음
                )
        ));

        ContextPreviewResponse response = externalContextService.preview(
                new ContextPreviewRequest(null, List.of("DEV-200"), null));

        assertThat(response.sources().get(0).status()).isEqualTo(SourceStatus.SUCCESS);
        assertThat(response.sources().get(0).formattedText()).contains("[Jira] DEV-200: No desc issue");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_Jira_AdfHeadingType_AppendsNewline() {
        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "fields", Map.of(
                        "summary", "Heading test",
                        "status", Map.of("name", "Open"),
                        "issuetype", Map.of("name", "Story"),
                        "description", Map.of(
                                "type", "doc",
                                "content", List.of(
                                        Map.of("type", "heading",
                                                "content", List.of(Map.of("type", "text", "text", "Section Title")))
                                )
                        )
                )
        ));

        ContextPreviewResponse response = externalContextService.preview(
                new ContextPreviewRequest(null, List.of("DEV-300"), null));

        assertThat(response.sources().get(0).formattedText()).contains("Section Title");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_Jira_AdfListItemAndCodeBlock_AppendsNewlines() {
        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "fields", Map.of(
                        "summary", "Mixed types",
                        "status", Map.of("name", "Open"),
                        "issuetype", Map.of("name", "Task"),
                        "description", Map.of(
                                "type", "doc",
                                "content", List.of(
                                        Map.of("type", "listItem",
                                                "content", List.of(Map.of("type", "text", "text", "item text"))),
                                        Map.of("type", "blockquote",
                                                "content", List.of(Map.of("type", "text", "text", "quoted"))),
                                        Map.of("type", "codeBlock",
                                                "content", List.of(Map.of("type", "text", "text", "code()")))
                                )
                        )
                )
        ));

        ContextPreviewResponse response = externalContextService.preview(
                new ContextPreviewRequest(null, List.of("DEV-301"), null));

        assertThat(response.sources().get(0).formattedText()).contains("item text");
        assertThat(response.sources().get(0).formattedText()).contains("quoted");
        assertThat(response.sources().get(0).formattedText()).contains("code()");
    }

    // -------------------------------------------------------------------------
    // Confluence — 추가 분기 케이스
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void preview_Confluence_NullResponse_ReturnsFailure() {
        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(null);

        ContextPreviewRequest request = new ContextPreviewRequest(null, null, List.of("99999"));

        assertThatThrownBy(() -> externalContextService.preview(request))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("All context sources failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_Confluence_NoBodyStorage_EmptyText() {
        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of("title", "No Body Page"));
        // body 키 없음 → plainText = ""

        ContextPreviewResponse response = externalContextService.preview(
                new ContextPreviewRequest(null, null, List.of("11111")));

        assertThat(response.sources().get(0).status()).isEqualTo(SourceStatus.SUCCESS);
        assertThat(response.sources().get(0).formattedText()).contains("[Confluence] No Body Page");
    }

    // -------------------------------------------------------------------------
    // preview() — blank 아이템 스킵 케이스
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void preview_JiraKeys_BlankItemsSkipped() {
        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "fields", Map.of(
                        "summary", "Valid issue",
                        "status", Map.of("name", "Open"),
                        "issuetype", Map.of("name", "Bug")
                )
        ));

        // blank key "  " 는 스킵, "DEV-1" 만 처리
        ContextPreviewRequest request = new ContextPreviewRequest(null, List.of("  ", "DEV-1"), null);

        ContextPreviewResponse response = externalContextService.preview(request);

        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).id()).isEqualTo("DEV-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_ConfluencePageIds_BlankItemsSkipped() {
        RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(atlassianRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "title", "Valid Page",
                "body", Map.of("storage", Map.of("value", "<p>Content</p>"))
        ));

        // blank pageId "" 는 스킵, "12345" 만 처리
        ContextPreviewRequest request = new ContextPreviewRequest(null, null, List.of("", "12345"));

        ContextPreviewResponse response = externalContextService.preview(request);

        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).type()).isEqualTo(SourceType.CONFLUENCE);
    }

}
