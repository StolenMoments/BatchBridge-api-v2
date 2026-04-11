package org.jh.batchbridge.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.client.ClientHttpResponse;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.ExternalBatchStatus;
import org.jh.batchbridge.dto.external.claude.ClaudeBatchResponse;
import org.jh.batchbridge.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class ClaudeBatchAdapterTest {

    // 기존 테스트: 실제 생성자로 helper 메서드 테스트
    private final ClaudeBatchAdapter adapter = new ClaudeBatchAdapter(
            "test-api-key",
            "message-batches-2024-09-24",
            10000,
            30000,
            1024,
            new ObjectMapper()
    );

    // HTTP 호출 테스트용: RestClient를 Mock으로 주입
    @Mock
    private RestClient mockRestClient;

    private ClaudeBatchAdapter mockAdapter;

    @BeforeEach
    void setUp() {
        mockAdapter = new ClaudeBatchAdapter(mockRestClient, 1024, new ObjectMapper());
    }

    // -------------------------------------------------------------------------
    // parseResults (기존 + 추가)
    // -------------------------------------------------------------------------

    @Test
    void parseResults_parsesSucceededAndErroredResults() {
        String jsonlBody = """
                {"custom_id":"101","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"hello 한글"}]}}}
                {"custom_id":"102","result":{"type":"errored","error":{"message":"bad request"}}}
                {"custom_id":"23","result":{"type":"errored","error":{"type":"error","error":{"details":{"error_visibility":"user_facing"},"type":"invalid_request_error","message":"max_tokens: 100000 > 64000, which is the maximum allowed number of output tokens for claude-haiku-4-5-20251001"},"request_id":null}}}
                {"custom_id":"24","result":{"type":"errored","error":{"type":"overloaded_error","message":"Overloaded"}}}
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of(101L, 102L, 23L, 24L));

        assertThat(results).hasSize(4);
        assertThat(results.get(101L)).isEqualTo(new PromptResult(true, "hello 한글", null));
        assertThat(results.get(102L)).isEqualTo(new PromptResult(false, null, "bad request"));
        assertThat(results.get(23L)).isEqualTo(new PromptResult(false, null, "max_tokens: 100000 > 64000, which is the maximum allowed number of output tokens for claude-haiku-4-5-20251001"));
        assertThat(results.get(24L)).isEqualTo(new PromptResult(false, null, "Overloaded"));
    }

    @Test
    void parseResults_EmptyInput_ReturnsEmptyMap() {
        Map<Long, PromptResult> results = adapter.parseResults("", Set.of(1L));
        assertThat(results).isEmpty();
    }

    @Test
    void parseResults_InvalidJsonLine_SkipsLine() {
        String jsonlBody = """
                not-valid-json
                {"custom_id":"1","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"ok"}]}}}
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of(1L));

        assertThat(results).hasSize(1);
        assertThat(results.get(1L).success()).isTrue();
    }

    @Test
    void parseResults_MissingCustomId_SkipsLine() {
        String jsonlBody = """
                {"result":{"type":"succeeded","message":{"content":[{"type":"text","text":"ok"}]}}}
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of(1L));

        assertThat(results).isEmpty();
    }

    @Test
    void parseResults_NonNumericCustomId_SkipsLine() {
        String jsonlBody = """
                {"custom_id":"not-a-number","result":{"type":"succeeded","message":{"content":[]}}}
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of(1L));

        assertThat(results).isEmpty();
    }

    @Test
    void parseResults_UnknownPromptId_SkipsLine() {
        String jsonlBody = """
                {"custom_id":"999","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"ok"}]}}}
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of(1L, 2L));

        assertThat(results).isEmpty();
    }

    @Test
    void parseResults_EmptyExpectedIds_AcceptsAllLines() {
        String jsonlBody = """
                {"custom_id":"42","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"hi"}]}}}
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of());

        assertThat(results).hasSize(1);
        assertThat(results.get(42L).success()).isTrue();
    }

    @Test
    void parseResults_ErrorWithNullMessage_UsesUnknownError() {
        String jsonlBody = """
                {"custom_id":"1","result":{"type":"errored","error":{}}}
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of(1L));

        assertThat(results.get(1L).success()).isFalse();
        assertThat(results.get(1L).errorMessage()).isEqualTo("Unknown error");
    }

    @Test
    void parseResults_MultipleTextContentItems_JoinedWithNewline() {
        String jsonlBody = """
                {"custom_id":"1","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"line1"},{"type":"text","text":"line2"}]}}}
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of(1L));

        assertThat(results.get(1L).responseContent()).isEqualTo("line1\nline2");
    }

    @Test
    void parseResults_NonTextContentType_Ignored() {
        String jsonlBody = """
                {"custom_id":"1","result":{"type":"succeeded","message":{"content":[{"type":"tool_use","id":"x"},{"type":"text","text":"real"}]}}}
                """;

        Map<Long, PromptResult> results = adapter.parseResults(jsonlBody, Set.of(1L));

        assertThat(results.get(1L).responseContent()).isEqualTo("real");
    }

    // -------------------------------------------------------------------------
    // getBaseName (기존)
    // -------------------------------------------------------------------------

    @Test
    void getBaseName_IdentifiesBaseNameWithoutDate() {
        assertThat(adapter.getBaseName("claude-3-5-sonnet-20240620")).isEqualTo("claude-3-5-sonnet");
        assertThat(adapter.getBaseName("claude-3-5-sonnet-20241022")).isEqualTo("claude-3-5-sonnet");
        assertThat(adapter.getBaseName("claude-3-opus-20240229")).isEqualTo("claude-3-opus");
        assertThat(adapter.getBaseName("claude-2.1")).isEqualTo("claude-2.1");
        assertThat(adapter.getBaseName("claude-sonnet-4-5-20250929")).isEqualTo("claude-sonnet");
        assertThat(adapter.getBaseName("claude-sonnet-4-20250514")).isEqualTo("claude-sonnet");
        assertThat(adapter.getBaseName("claude-3-haiku-20240307")).isEqualTo("claude-3-haiku");
        assertThat(adapter.getBaseName("claude-opus-4-5-20251101")).isEqualTo("claude-opus");
        assertThat(adapter.getBaseName("claude-haiku-4-5-20251001")).isEqualTo("claude-haiku");
        assertThat(adapter.getBaseName("claude-opus-4-1-20250805")).isEqualTo("claude-opus");
        assertThat(adapter.getBaseName("claude-opus-4-20250514")).isEqualTo("claude-opus");
        assertThat(adapter.getBaseName("claude-3-5-sonnet-latest")).isEqualTo("claude-3-5-sonnet");
        assertThat(adapter.getBaseName("claude-3-7-sonnet-latest")).isEqualTo("claude-3-7-sonnet");
    }

    // -------------------------------------------------------------------------
    // filterLatestModels (기존)
    // -------------------------------------------------------------------------

    @Test
    void filterLatestModels_SelectsCorrectModels() {
        var models = java.util.List.of(
            new ClaudeModelData("claude-sonnet-4-5-20250929", "Claude Sonnet 4.5"),
            new ClaudeModelData("claude-sonnet-4-20250514", "Claude Sonnet 4"),
            new ClaudeModelData("claude-3-haiku-20240307", "Claude Haiku 3"),
            new ClaudeModelData("claude-sonnet-4-6", "Claude Sonnet 4.6"),
            new ClaudeModelData("claude-opus-4-5-20251101", "Claude Opus 4.5"),
            new ClaudeModelData("claude-opus-4-6", "Claude Opus 4.6"),
            new ClaudeModelData("claude-haiku-4-5-20251001", "Claude Haiku 4.5"),
            new ClaudeModelData("claude-opus-4-1-20250805", "Claude Opus 4.1"),
            new ClaudeModelData("claude-opus-4-20250514", "Claude Opus 4"),
            new ClaudeModelData("grok-3", "Grok 3"),
            new ClaudeModelData("gemini-2.0-flash", "Gemini 2.0 Flash")
        );

        java.util.Map<String, org.jh.batchbridge.dto.response.ModelInfo> latestModels = new java.util.HashMap<>();
        for (ClaudeModelData data : models) {
            String id = data.id();
            String baseName = adapter.getBaseName(id);
            org.jh.batchbridge.dto.response.ModelInfo current = new org.jh.batchbridge.dto.response.ModelInfo(id, data.displayName());
            org.jh.batchbridge.dto.response.ModelInfo existing = latestModels.get(baseName);
            if (existing == null || id.compareTo(existing.id()) > 0) {
                latestModels.put(baseName, current);
            }
        }

        assertThat(latestModels.get("claude-sonnet").id()).isEqualTo("claude-sonnet-4-6");
        assertThat(latestModels.get("claude-opus").id()).isEqualTo("claude-opus-4-6");
        assertThat(latestModels.get("claude-haiku").id()).isEqualTo("claude-haiku-4-5-20251001");
        assertThat(latestModels.get("claude-3-haiku").id()).isEqualTo("claude-3-haiku-20240307");
    }

    // -------------------------------------------------------------------------
    // buildSubmitRequestBody (기존 + 추가)
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void buildSubmitRequestBody_includesAttachmentsInUserPrompt() {
        BatchSubmitRequest request = new BatchSubmitRequest("claude-3", java.util.List.of(
                new BatchSubmitRequest.PromptItem(1L, "sys", "user prompt", java.util.List.of(
                        new BatchSubmitRequest.AttachmentItem("file1.txt", "content1"),
                        new BatchSubmitRequest.AttachmentItem("file2.txt", "content2")
                ))
        ));

        Map<String, Object> body = adapter.buildSubmitRequestBody(request);
        var requests = (java.util.List<Map<String, Object>>) body.get("requests");
        var params = (Map<String, Object>) requests.get(0).get("params");
        var messages = (java.util.List<Map<String, Object>>) params.get("messages");
        String content = (String) messages.get(0).get("content");

        assertThat(content).contains("<attachments>");
        assertThat(content).contains("<attachment name=\"file1.txt\">");
        assertThat(content).contains("content1");
        assertThat(content).contains("<attachment name=\"file2.txt\">");
        assertThat(content).contains("content2");
        assertThat(content).contains("user prompt");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildSubmitRequestBody_NoSystemPrompt_OmitsSystemField() {
        BatchSubmitRequest request = new BatchSubmitRequest("claude-3", List.of(
                new BatchSubmitRequest.PromptItem(1L, null, "user", List.of())
        ));

        Map<String, Object> body = adapter.buildSubmitRequestBody(request);
        var requests = (List<Map<String, Object>>) body.get("requests");
        var params = (Map<String, Object>) requests.get(0).get("params");

        assertThat(params).doesNotContainKey("system");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildSubmitRequestBody_SetsMaxTokensAndModel() {
        BatchSubmitRequest request = new BatchSubmitRequest("claude-sonnet", List.of(
                new BatchSubmitRequest.PromptItem(99L, "sys", "user", List.of())
        ));

        Map<String, Object> body = adapter.buildSubmitRequestBody(request);
        var requests = (List<Map<String, Object>>) body.get("requests");
        var params = (Map<String, Object>) requests.get(0).get("params");

        assertThat(params.get("model")).isEqualTo("claude-sonnet");
        assertThat(params.get("max_tokens")).isEqualTo(1024);
        assertThat(requests.get(0).get("custom_id")).isEqualTo("99");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildSubmitRequestBody_NoAttachments_OnlyUserPromptInContent() {
        BatchSubmitRequest request = new BatchSubmitRequest("claude-3", List.of(
                new BatchSubmitRequest.PromptItem(1L, "sys", "just user", List.of())
        ));

        Map<String, Object> body = adapter.buildSubmitRequestBody(request);
        var requests = (List<Map<String, Object>>) body.get("requests");
        var params = (Map<String, Object>) requests.get(0).get("params");
        var messages = (List<Map<String, Object>>) params.get("messages");
        String content = (String) messages.get(0).get("content");

        assertThat(content).isEqualTo("just user");
        assertThat(content).doesNotContain("<attachments>");
    }

    // -------------------------------------------------------------------------
    // submitBatch (HTTP mock 기반)
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void submitBatch_Success_ReturnsExternalBatchId() {
        RequestBodyUriSpec postSpec = mock(RequestBodyUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);
        ClaudeBatchResponse claudeResponse = new ClaudeBatchResponse("batch_abc123", null, null, null);

        when(mockRestClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postSpec);
        when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(claudeResponse);

        BatchSubmitRequest request = new BatchSubmitRequest("claude-3",
                List.of(new BatchSubmitRequest.PromptItem(1L, null, "user", List.of())));

        ExternalBatchId result = mockAdapter.submitBatch(request);

        assertThat(result.value()).isEqualTo("batch_abc123");
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitBatch_NullResponse_ThrowsExternalApiException() {
        RequestBodyUriSpec postSpec = mock(RequestBodyUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(mockRestClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postSpec);
        when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(null);

        BatchSubmitRequest request = new BatchSubmitRequest("claude-3",
                List.of(new BatchSubmitRequest.PromptItem(1L, null, "user", List.of())));

        assertThatThrownBy(() -> mockAdapter.submitBatch(request))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("does not contain id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitBatch_BlankId_ThrowsExternalApiException() {
        RequestBodyUriSpec postSpec = mock(RequestBodyUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);
        ClaudeBatchResponse claudeResponse = new ClaudeBatchResponse("  ", null, null, null);

        when(mockRestClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postSpec);
        when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(claudeResponse);

        BatchSubmitRequest request = new BatchSubmitRequest("claude-3",
                List.of(new BatchSubmitRequest.PromptItem(1L, null, "user", List.of())));

        assertThatThrownBy(() -> mockAdapter.submitBatch(request))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitBatch_HttpClientError_ThrowsExternalApiException() {
        RequestBodyUriSpec postSpec = mock(RequestBodyUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(mockRestClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postSpec);
        when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        BatchSubmitRequest request = new BatchSubmitRequest("claude-3",
                List.of(new BatchSubmitRequest.PromptItem(1L, null, "user", List.of())));

        assertThatThrownBy(() -> mockAdapter.submitBatch(request))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to create Claude batch");
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitBatch_HttpServerError_ThrowsExternalApiException() {
        RequestBodyUriSpec postSpec = mock(RequestBodyUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(mockRestClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postSpec);
        when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        BatchSubmitRequest request = new BatchSubmitRequest("claude-3",
                List.of(new BatchSubmitRequest.PromptItem(1L, null, "user", List.of())));

        assertThatThrownBy(() -> mockAdapter.submitBatch(request))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitBatch_RestClientException_ThrowsExternalApiException() {
        RequestBodyUriSpec postSpec = mock(RequestBodyUriSpec.class);

        when(mockRestClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenThrow(new RestClientException("connection refused"));

        BatchSubmitRequest request = new BatchSubmitRequest("claude-3",
                List.of(new BatchSubmitRequest.PromptItem(1L, null, "user", List.of())));

        assertThatThrownBy(() -> mockAdapter.submitBatch(request))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to create Claude batch");
    }

    // -------------------------------------------------------------------------
    // fetchStatus (HTTP mock 기반)
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void fetchStatus_Ended_ReturnsCompleted() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);
        ClaudeBatchResponse claudeResponse = new ClaudeBatchResponse("batch_1", null, "ended", null);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(claudeResponse);

        var result = mockAdapter.fetchStatus(new ExternalBatchId("batch_1"));

        assertThat(result.status()).isEqualTo(ExternalBatchStatus.COMPLETED);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchStatus_Failed_ReturnsFailedStatus() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);
        ClaudeBatchResponse claudeResponse = new ClaudeBatchResponse("batch_1", null, "failed", null);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(claudeResponse);

        var result = mockAdapter.fetchStatus(new ExternalBatchId("batch_1"));

        assertThat(result.status()).isEqualTo(ExternalBatchStatus.FAILED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchStatus_Errored_ReturnsFailedStatus() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);
        ClaudeBatchResponse claudeResponse = new ClaudeBatchResponse("batch_1", null, "errored", null);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(claudeResponse);

        var result = mockAdapter.fetchStatus(new ExternalBatchId("batch_1"));

        assertThat(result.status()).isEqualTo(ExternalBatchStatus.FAILED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchStatus_Canceling_ReturnsFailedStatus() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);
        ClaudeBatchResponse claudeResponse = new ClaudeBatchResponse("batch_1", null, "canceling", null);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(claudeResponse);

        var result = mockAdapter.fetchStatus(new ExternalBatchId("batch_1"));

        assertThat(result.status()).isEqualTo(ExternalBatchStatus.FAILED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchStatus_Expired_ReturnsFailedStatus() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);
        ClaudeBatchResponse claudeResponse = new ClaudeBatchResponse("batch_1", null, "expired", null);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(claudeResponse);

        var result = mockAdapter.fetchStatus(new ExternalBatchId("batch_1"));

        assertThat(result.status()).isEqualTo(ExternalBatchStatus.FAILED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchStatus_InProgress_ReturnsInProgress() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);
        ClaudeBatchResponse claudeResponse = new ClaudeBatchResponse("batch_1", null, "in_progress", null);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(claudeResponse);

        var result = mockAdapter.fetchStatus(new ExternalBatchId("batch_1"));

        assertThat(result.status()).isEqualTo(ExternalBatchStatus.IN_PROGRESS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchStatus_NullProcessingStatus_ReturnsInProgress() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);
        ClaudeBatchResponse claudeResponse = new ClaudeBatchResponse("batch_1", null, null, null);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(claudeResponse);

        var result = mockAdapter.fetchStatus(new ExternalBatchId("batch_1"));

        assertThat(result.status()).isEqualTo(ExternalBatchStatus.IN_PROGRESS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchStatus_WithErrorField_FormatsErrorMessage() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);
        ClaudeBatchResponse claudeResponse = new ClaudeBatchResponse(
                "batch_1", null, "ended",
                new ClaudeBatchResponse.ClaudeError("server_error", "internal failure"));

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(claudeResponse);

        var result = mockAdapter.fetchStatus(new ExternalBatchId("batch_1"));

        assertThat(result.errorMessage()).isEqualTo("[server_error] internal failure");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchStatus_NullResponse_ThrowsExternalApiException() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class)).thenReturn(null);

        assertThatThrownBy(() -> mockAdapter.fetchStatus(new ExternalBatchId("batch_1")))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchStatus_HttpClientError_ThrowsExternalApiException() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ClaudeBatchResponse.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> mockAdapter.fetchStatus(new ExternalBatchId("batch_1")))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to fetch Claude batch status");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchStatus_RestClientException_ThrowsExternalApiException() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenThrow(new RestClientException("timeout"));

        assertThatThrownBy(() -> mockAdapter.fetchStatus(new ExternalBatchId("batch_1")))
                .isInstanceOf(ExternalApiException.class);
    }

    // -------------------------------------------------------------------------
    // fetchResults (HTTP mock 기반)
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void fetchResults_NullPrompts_ReturnsResult() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        Map<Long, PromptResult> expected = Map.of();

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.exchange(any())).thenReturn(expected);

        var result = mockAdapter.fetchResults(new ExternalBatchId("batch_1"), null);

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchResults_HttpClientError_ThrowsExternalApiException() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.exchange(any())).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> mockAdapter.fetchResults(new ExternalBatchId("batch_1"), List.of()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to fetch Claude batch results");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchResults_RestClientException_ThrowsExternalApiException() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.exchange(any())).thenThrow(new RestClientException("timeout"));

        assertThatThrownBy(() -> mockAdapter.fetchResults(new ExternalBatchId("batch_1"), List.of()))
                .isInstanceOf(ExternalApiException.class);
    }

    // -------------------------------------------------------------------------
    // parseResultsFromStream (스트리밍 파서 — package-private 직접 테스트)
    // -------------------------------------------------------------------------

    private static ClientHttpResponse mockStreamResponse(String jsonlBody) throws IOException {
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(response.getBody()).thenReturn(
                new ByteArrayInputStream(jsonlBody.getBytes(StandardCharsets.UTF_8)));
        return response;
    }

    @Test
    void parseResultsFromStream_ValidSucceeded_ParsesCorrectly() throws IOException {
        String jsonl = """
                {"custom_id":"101","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"hello"}]}}}
                """;
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of(101L));
        assertThat(results.get(101L)).isEqualTo(new PromptResult(true, "hello", null));
    }

    @Test
    void parseResultsFromStream_MultipleResults_ParsesAll() throws IOException {
        String jsonl = """
                {"custom_id":"1","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"line1"},{"type":"text","text":"line2"}]}}}
                {"custom_id":"2","result":{"type":"errored","error":{"message":"bad input"}}}
                """;
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of(1L, 2L));
        assertThat(results.get(1L).responseContent()).isEqualTo("line1\nline2");
        assertThat(results.get(2L)).isEqualTo(new PromptResult(false, null, "bad input"));
    }

    @Test
    void parseResultsFromStream_EmptyInput_ReturnsEmptyMap() throws IOException {
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(""), Set.of(1L));
        assertThat(results).isEmpty();
    }

    @Test
    void parseResultsFromStream_BlankLines_Skipped() throws IOException {
        String jsonl = "\n\n\n";
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of(1L));
        assertThat(results).isEmpty();
    }

    @Test
    void parseResultsFromStream_InvalidJsonLine_SkipsLine() throws IOException {
        String jsonl = """
                not-valid-json
                {"custom_id":"1","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"ok"}]}}}
                """;
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of(1L));
        assertThat(results).hasSize(1);
        assertThat(results.get(1L).success()).isTrue();
    }

    @Test
    void parseResultsFromStream_MissingCustomId_SkipsLine() throws IOException {
        String jsonl = """
                {"result":{"type":"succeeded","message":{"content":[{"type":"text","text":"ok"}]}}}
                """;
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of(1L));
        assertThat(results).isEmpty();
    }

    @Test
    void parseResultsFromStream_NonNumericCustomId_SkipsLine() throws IOException {
        String jsonl = """
                {"custom_id":"not-a-number","result":{"type":"succeeded","message":{"content":[]}}}
                """;
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of(1L));
        assertThat(results).isEmpty();
    }

    @Test
    void parseResultsFromStream_UnknownPromptId_SkipsLine() throws IOException {
        String jsonl = """
                {"custom_id":"999","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"ok"}]}}}
                """;
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of(1L, 2L));
        assertThat(results).isEmpty();
    }

    @Test
    void parseResultsFromStream_EmptyExpectedIds_AcceptsAllLines() throws IOException {
        String jsonl = """
                {"custom_id":"42","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"hi"}]}}}
                """;
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of());
        assertThat(results.get(42L).success()).isTrue();
    }

    @Test
    void parseResultsFromStream_ErrorWithNestedMessage_ParsesNestedMessage() throws IOException {
        String jsonl = """
                {"custom_id":"23","result":{"type":"errored","error":{"type":"error","error":{"message":"nested error detail"}}}}
                """;
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of(23L));
        assertThat(results.get(23L).errorMessage()).isEqualTo("nested error detail");
    }

    @Test
    void parseResultsFromStream_ErrorWithNullMessage_UsesUnknownError() throws IOException {
        String jsonl = """
                {"custom_id":"1","result":{"type":"errored","error":{}}}
                """;
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of(1L));
        assertThat(results.get(1L).errorMessage()).isEqualTo("Unknown error");
    }

    @Test
    void parseResultsFromStream_DuplicatePromptId_LastValueWins() throws IOException {
        String jsonl = """
                {"custom_id":"1","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"first"}]}}}
                {"custom_id":"1","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"second"}]}}}
                """;
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of(1L));
        assertThat(results.get(1L).responseContent()).isEqualTo("second");
    }

    @Test
    void parseResultsFromStream_NonTextContentType_Ignored() throws IOException {
        String jsonl = """
                {"custom_id":"1","result":{"type":"succeeded","message":{"content":[{"type":"tool_use","id":"x"},{"type":"text","text":"real"}]}}}
                """;
        Map<Long, PromptResult> results = adapter.parseResultsFromStream(mockStreamResponse(jsonl), Set.of(1L));
        assertThat(results.get(1L).responseContent()).isEqualTo("real");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchResults_Non2xxResponse_ThrowsExternalApiException() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse mockHttpResponse =
                mock(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse.class);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString(), anyString())).thenReturn(getSpec);
        when(getSpec.exchange(any())).thenAnswer(invocation -> {
            RestClient.RequestHeadersSpec.ExchangeFunction<Object> fn = invocation.getArgument(0);
            when(mockHttpResponse.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
            try {
                return fn.exchange(null, mockHttpResponse);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertThatThrownBy(() -> mockAdapter.fetchResults(new ExternalBatchId("batch_1"), List.of()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to fetch Claude batch results");
    }

    // -------------------------------------------------------------------------
    // fetchSupportedModels (HTTP mock 기반)
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void fetchSupportedModels_NullResponse_ReturnsEmptyList() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenReturn(null);

        var result = mockAdapter.fetchSupportedModels();

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchSupportedModels_Exception_ThrowsExternalApiException() {
        RequestHeadersUriSpec getSpec = mock(RequestHeadersUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(mockRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString())).thenReturn(getSpec);
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenThrow(new RestClientException("timeout"));

        assertThatThrownBy(() -> mockAdapter.fetchSupportedModels())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to fetch Claude models");
    }

    // -------------------------------------------------------------------------
    // getSupportedModelPrefix
    // -------------------------------------------------------------------------

    @Test
    void getSupportedModelPrefix_ReturnsClaude() {
        assertThat(adapter.getSupportedModelPrefix()).isEqualTo("claude-");
    }

    // Helper record for filterLatestModels test
    private record ClaudeModelData(String id, String displayName) {}
}
