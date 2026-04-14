package org.jh.batchbridge.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.domain.PromptType;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.ExternalBatchStatus;
import org.jh.batchbridge.dto.response.ModelInfo;
import org.jh.batchbridge.exception.ExternalApiException;
import org.jh.batchbridge.service.MediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XAIBatchAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer server;
    private MediaStorageService mediaStorageService;
    private XAIBatchAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.x.ai");
        server = MockRestServiceServer.bindTo(builder).build();
        mediaStorageService = mock(MediaStorageService.class);
        adapter = new XAIBatchAdapter(builder.build(), objectMapper, mediaStorageService);
    }

    // ─── supportsPromptType ────────────────────────────────────────────────────

    @Test
    void supportsPromptTypeReturnsTrueForAllTypes() {
        assertThat(adapter.supportsPromptType(PromptType.TEXT)).isTrue();
        assertThat(adapter.supportsPromptType(PromptType.IMAGE_GENERATION)).isTrue();
        assertThat(adapter.supportsPromptType(PromptType.IMAGE_EDIT)).isTrue();
        assertThat(adapter.supportsPromptType(PromptType.VIDEO_GENERATION)).isTrue();
        assertThat(adapter.supportsPromptType(PromptType.VIDEO_EDIT)).isTrue();
    }

    // ─── buildUserInput ────────────────────────────────────────────────────────

    @Test
    void buildUserInputIncludesSystemPromptAndAttachments() {
        BatchSubmitRequest.PromptItem prompt = new BatchSubmitRequest.PromptItem(
                1L,
                "system message",
                "user message",
                null,
                null,
                List.of(
                        new BatchSubmitRequest.AttachmentItem("a.txt", "A"),
                        new BatchSubmitRequest.AttachmentItem("b.txt", "B")
                )
        );

        List<Map<String, Object>> input = adapter.buildUserInput(prompt);

        assertThat(input).hasSize(2);
        assertThat(input.get(0)).containsEntry("role", "system").containsEntry("content", "system message");
        assertThat(input.get(1)).containsEntry("role", "user");
        assertThat((String) input.get(1).get("content"))
                .contains("<attachments>")
                .contains("<attachment name=\"a.txt\">A</attachment>")
                .contains("<attachment name=\"b.txt\">B</attachment>")
                .contains("user message");
    }

    @Test
    void buildUserInputEscapesXmlSpecialCharsInAttachments() {
        BatchSubmitRequest.PromptItem prompt = new BatchSubmitRequest.PromptItem(
                1L,
                null,
                "user message",
                null,
                null,
                List.of(new BatchSubmitRequest.AttachmentItem("<evil>", "content with & \"quotes\""))
        );

        String content = (String) adapter.buildUserInput(prompt).getLast().get("content");

        assertThat(content)
                .contains("&lt;evil&gt;")
                .doesNotContain("<evil>")
                .contains("content with &amp; &quot;quotes&quot;")
                .doesNotContain("& \"quotes\"");
    }

    @Test
    void buildUserInputUsesRawPromptWhenNoAttachmentsOrSystemPrompt() {
        BatchSubmitRequest.PromptItem prompt = new BatchSubmitRequest.PromptItem(
                1L,
                null,
                "plain prompt",
                null,
                null,
                List.of()
        );

        assertThat(adapter.buildUserInput(prompt))
                .containsExactly(Map.of("role", "user", "content", "plain prompt"));
    }

    // ─── parseResultsPage ─────────────────────────────────────────────────────

    @Test
    void parseResultsPageParsesSucceededAndFailedResults() {
        Map<Long, PromptResult> results = adapter.parseResultsPage(List.of(
                successResult("101", "hello"),
                failedResult("102", "bad request")
        ), Set.of(101L, 102L), null);

        assertThat(results).containsEntry(101L, new PromptResult(true, "hello", null, null));
        assertThat(results).containsEntry(102L, new PromptResult(false, null, "bad request", null));
    }

    @Test
    void parseResultsPageSkipsInvalidOrUnknownResults() {
        Map<Long, PromptResult> results = adapter.parseResultsPage(List.of(
                new XAIBatchAdapter.XAIResultItem("abc", new XAIBatchAdapter.XAIBatchResult(null, null, null, null), null),
                new XAIBatchAdapter.XAIResultItem("999", new XAIBatchAdapter.XAIBatchResult(null, null, null, null), "ignored"),
                new XAIBatchAdapter.XAIResultItem("101", null, null),
                failedResult("102", null)
        ), Set.of(101L, 102L), null);

        assertThat(results).hasSize(1);
        assertThat(results).containsEntry(102L, new PromptResult(false, null, "Unknown error", null));
    }

    @Test
    void parseResultsPageAcceptsAllNumericIdsWhenExpectedIdsEmpty() {
        Map<Long, PromptResult> results = adapter.parseResultsPage(List.of(
                successResult("1", "first"),
                failedResult("2", "error")
        ), Set.of(), null);

        assertThat(results).hasSize(2);
    }

    @Test
    void parseResultsPageDownloadsAndStoresImageResponse() {
        when(mediaStorageService.download(5L, 101L, "https://api.x.ai/result/image.png"))
                .thenReturn("/app/media/5/101.png");

        Map<Long, PromptResult> results = adapter.parseResultsPage(List.of(
                new XAIBatchAdapter.XAIResultItem(
                        "101",
                        new XAIBatchAdapter.XAIBatchResult(
                                null,
                                new XAIBatchAdapter.XAIImageResponse("https://api.x.ai/result/image.png"),
                                null,
                                null
                        ),
                        null
                )
        ), Set.of(101L), 5L);

        assertThat(results).containsEntry(101L, new PromptResult(true, null, null, "/app/media/5/101.png"));
        verify(mediaStorageService).download(5L, 101L, "https://api.x.ai/result/image.png");
    }

    @Test
    void parseResultsPageDownloadsAndStoresVideoResponse() {
        when(mediaStorageService.download(5L, 102L, "https://api.x.ai/result/video.mp4"))
                .thenReturn("/app/media/5/102.mp4");

        Map<Long, PromptResult> results = adapter.parseResultsPage(List.of(
                new XAIBatchAdapter.XAIResultItem(
                        "102",
                        new XAIBatchAdapter.XAIBatchResult(
                                null,
                                null,
                                new XAIBatchAdapter.XAIVideoResponse("https://api.x.ai/result/video.mp4"),
                                null
                        ),
                        null
                )
        ), Set.of(102L), 5L);

        assertThat(results).containsEntry(102L, new PromptResult(true, null, null, "/app/media/5/102.mp4"));
        verify(mediaStorageService).download(5L, 102L, "https://api.x.ai/result/video.mp4");
    }

    @Test
    void parseResultsPageTreatsXAIBatchErrorAsFailure() {
        Map<Long, PromptResult> results = adapter.parseResultsPage(List.of(
                new XAIBatchAdapter.XAIResultItem(
                        "103",
                        new XAIBatchAdapter.XAIBatchResult(
                                null,
                                null,
                                null,
                                new XAIBatchAdapter.XAIBatchError("Prompt violates content policy", "content_policy_violation")
                        ),
                        null
                )
        ), Set.of(103L), null);

        assertThat(results).containsEntry(103L,
                new PromptResult(false, null, "[content_policy_violation] Prompt violates content policy", null));
    }

    @Test
    void parseResultsPageIncludesErrorCodeInMessageWhenPresent() {
        Map<Long, PromptResult> results = adapter.parseResultsPage(List.of(
                new XAIBatchAdapter.XAIResultItem(
                        "104",
                        new XAIBatchAdapter.XAIBatchResult(null, null, null,
                                new XAIBatchAdapter.XAIBatchError(null, "rate_limit_exceeded")),
                        null
                )
        ), Set.of(104L), null);

        assertThat(results.get(104L).success()).isFalse();
        assertThat(results.get(104L).errorMessage()).isEqualTo("[rate_limit_exceeded] Unknown error");
    }

    // ─── toBatchStatus ────────────────────────────────────────────────────────

    @Test
    void toBatchStatusMapsExpectedStates() {
        assertThat(adapter.toBatchStatus(null)).isEqualTo(ExternalBatchStatus.IN_PROGRESS);
        assertThat(adapter.toBatchStatus(new XAIBatchAdapter.XAIBatchState(10, 2, 7, 1, 0)))
                .isEqualTo(ExternalBatchStatus.IN_PROGRESS);
        assertThat(adapter.toBatchStatus(new XAIBatchAdapter.XAIBatchState(10, 0, 10, 0, 0)))
                .isEqualTo(ExternalBatchStatus.COMPLETED);
        assertThat(adapter.toBatchStatus(new XAIBatchAdapter.XAIBatchState(10, 0, 8, 2, 0)))
                .isEqualTo(ExternalBatchStatus.FAILED);
        assertThat(adapter.toBatchStatus(new XAIBatchAdapter.XAIBatchState(10, 0, 0, 10, 0)))
                .isEqualTo(ExternalBatchStatus.FAILED);
        assertThat(adapter.toBatchStatus(new XAIBatchAdapter.XAIBatchState(10, 0, 0, 0, 0)))
                .isEqualTo(ExternalBatchStatus.IN_PROGRESS);
    }

    // ─── submitBatch ──────────────────────────────────────────────────────────

    @Test
    void submitBatchCreatesBatchAndAddsRequests() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"batch_id":"batch_123"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_123/requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "batch_requests": [
                            {
                              "batch_request_id": "101",
                              "batch_request": {
                                "responses": {
                                  "model": "grok-3",
                                  "input": [
                                    {"role": "system", "content": "sys"},
                                    {"role": "user", "content": "<attachments>\\n<attachment name=\\"doc.txt\\">body</attachment>\\n</attachments>\\n\\nuser"}
                                  ]
                                }
                              }
                            }
                          ]
                        }
                        """, true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ExternalBatchId batchId = adapter.submitBatch(new BatchSubmitRequest(
                "grok-3",
                List.of(new BatchSubmitRequest.PromptItem(
                        101L,
                        "sys",
                        "user",
                        null,
                        null,
                        List.of(new BatchSubmitRequest.AttachmentItem("doc.txt", "body"))
                ))
        ));

        assertThat(batchId).isEqualTo(new ExternalBatchId("batch_123"));
        server.verify();
    }

    @Test
    void submitBatchSendsImageGenerationRequest() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"batch_id":"batch_456"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_456/requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "batch_requests": [
                            {
                              "batch_request_id": "201",
                              "batch_request": {
                                "image_generation": {
                                  "model": "grok-imagine-image",
                                  "prompt": "a beautiful sunset",
                                  "n": 1,
                                  "response_format": "url"
                                }
                              }
                            }
                          ]
                        }
                        """, true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        adapter.submitBatch(new BatchSubmitRequest(
                "grok-imagine-image",
                List.of(new BatchSubmitRequest.PromptItem(
                        201L, null, "a beautiful sunset", PromptType.IMAGE_GENERATION, null, List.of()
                ))
        ));

        server.verify();
    }

    @Test
    void submitBatchSendsImageEditRequest() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"batch_id":"batch_456"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_456/requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "batch_requests": [
                            {
                              "batch_request_id": "202",
                              "batch_request": {
                                "url": "/v1/images/edits",
                                "body": {
                                  "model": "grok-imagine-image",
                                  "prompt": "add a rainbow",
                                  "image": {"url": "https://example.com/source.jpg"}
                                }
                              }
                            }
                          ]
                        }
                        """, true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        adapter.submitBatch(new BatchSubmitRequest(
                "grok-imagine-image",
                List.of(new BatchSubmitRequest.PromptItem(
                        202L, null, "add a rainbow", PromptType.IMAGE_EDIT,
                        "https://example.com/source.jpg", List.of()
                ))
        ));

        server.verify();
    }

    @Test
    void submitBatchSendsVideoGenerationRequest() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"batch_id":"batch_456"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_456/requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "batch_requests": [
                            {
                              "batch_request_id": "203",
                              "batch_request": {
                                "video_generation": {
                                  "model": "grok-imagine-video",
                                  "prompt": "a flowing river"
                                }
                              }
                            }
                          ]
                        }
                        """, true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        adapter.submitBatch(new BatchSubmitRequest(
                "grok-imagine-video",
                List.of(new BatchSubmitRequest.PromptItem(
                        203L, null, "a flowing river", PromptType.VIDEO_GENERATION, null, List.of()
                ))
        ));

        server.verify();
    }

    @Test
    void submitBatchSendsVideoEditRequest() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"batch_id":"batch_456"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_456/requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "batch_requests": [
                            {
                              "batch_request_id": "204",
                              "batch_request": {
                                "url": "/v1/videos/edits",
                                "body": {
                                  "model": "grok-imagine-video",
                                  "prompt": "slow motion",
                                  "video": {"url": "https://example.com/source.mp4"}
                                }
                              }
                            }
                          ]
                        }
                        """, true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        adapter.submitBatch(new BatchSubmitRequest(
                "grok-imagine-video",
                List.of(new BatchSubmitRequest.PromptItem(
                        204L, null, "slow motion", PromptType.VIDEO_EDIT,
                        "https://example.com/source.mp4", List.of()
                ))
        ));

        server.verify();
    }

    @Test
    void submitBatchThrowsWhenCreateResponseDoesNotContainBatchId() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.submitBatch(new BatchSubmitRequest("grok-3", List.of())))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("batch_id");
    }

    @Test
    void submitBatchWrapsHttpErrors() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches"))
                .andRespond(withServerError().body("boom"));

        assertThatThrownBy(() -> adapter.submitBatch(new BatchSubmitRequest("grok-3", List.of())))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to submit xAI batch");
    }

    @Test
    void submitBatchThrowsWhenImageEditMissingReferenceUrl() {
        assertThatThrownBy(() -> adapter.submitBatch(new BatchSubmitRequest(
                "grok-imagine-image",
                List.of(new BatchSubmitRequest.PromptItem(
                        205L, null, "add a rainbow", PromptType.IMAGE_EDIT, null, List.of()
                ))
        )))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("referenceMediaUrl is required for IMAGE_EDIT");
    }

    @Test
    void submitBatchThrowsWhenVideoEditMissingReferenceUrl() {
        assertThatThrownBy(() -> adapter.submitBatch(new BatchSubmitRequest(
                "grok-imagine-video",
                List.of(new BatchSubmitRequest.PromptItem(
                        206L, null, "slow motion", PromptType.VIDEO_EDIT, null, List.of()
                ))
        )))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("referenceMediaUrl is required for VIDEO_EDIT");
    }

    // ─── fetchStatus ──────────────────────────────────────────────────────────

    @Test
    void fetchStatusReturnsFailedWhenAnyRequestFailed() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "batch_id": "batch_123",
                          "state": {
                            "num_requests": 2,
                            "num_pending": 0,
                            "num_success": 1,
                            "num_error": 1,
                            "num_cancelled": 0
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.fetchStatus(new ExternalBatchId("batch_123")))
                .isEqualTo(new BatchStatusResult(ExternalBatchStatus.FAILED, null));
    }

    @Test
    void fetchStatusReturnsCompletedWhenAllSucceeded() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "batch_id": "batch_123",
                          "state": {
                            "num_requests": 2,
                            "num_pending": 0,
                            "num_success": 2,
                            "num_error": 0,
                            "num_cancelled": 0
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.fetchStatus(new ExternalBatchId("batch_123")).status())
                .isEqualTo(ExternalBatchStatus.COMPLETED);
    }

    @Test
    void fetchStatusReturnsFailedWhenAllRequestsFailed() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_123"))
                .andRespond(withSuccess("""
                        {
                          "batch_id": "batch_123",
                          "state": {
                            "num_requests": 2,
                            "num_pending": 0,
                            "num_success": 0,
                            "num_error": 2,
                            "num_cancelled": 0
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.fetchStatus(new ExternalBatchId("batch_123")).status())
                .isEqualTo(ExternalBatchStatus.FAILED);
    }

    @Test
    void fetchStatusThrowsOnHttpError() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_123"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.fetchStatus(new ExternalBatchId("batch_123")))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to fetch xAI batch status");
    }

    // ─── fetchSupportedModels ─────────────────────────────────────────────────

    @Test
    void fetchSupportedModelsReturnsLatestMainGrokModelPlusStaticMediaModels() {
        server.expect(once(), requestTo("https://api.x.ai/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"id": "grok-4-0709", "created": 1752019200, "object": "model", "owned_by": "xai"},
                            {"id": "grok-code-fast-1", "created": 1755993600, "object": "model", "owned_by": "xai"},
                            {"id": "grok-3", "created": 1743724800, "object": "model", "owned_by": "xai"},
                            {"id": "grok-3-mini", "created": 1743724800, "object": "model", "owned_by": "xai"},
                            {"id": "grok-2-image-1212", "created": 1736726400, "object": "model", "owned_by": "xai"},
                            {"id": "grok-2-vision-1212", "created": 1733961600, "object": "model", "owned_by": "xai"}
                          ],
                          "object": "list"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.fetchSupportedModels())
                .containsExactlyInAnyOrder(
                        new ModelInfo("grok-4-0709", "grok-4-0709", List.of(PromptType.TEXT)),
                        new ModelInfo("grok-imagine-image", "grok-imagine-image",
                                List.of(PromptType.IMAGE_GENERATION, PromptType.IMAGE_EDIT)),
                        new ModelInfo("grok-imagine-video", "grok-imagine-video",
                                List.of(PromptType.VIDEO_GENERATION, PromptType.VIDEO_EDIT))
                );
    }

    @Test
    void fetchSupportedModelsReturnsOnlyStaticModelsWhenAllTextModelsAreSpecialized() {
        server.expect(once(), requestTo("https://api.x.ai/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"id": "grok-code-fast-1", "created": 1755993600, "object": "model", "owned_by": "xai"},
                            {"id": "grok-2-image-1212", "created": 1736726400, "object": "model", "owned_by": "xai"}
                          ],
                          "object": "list"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.fetchSupportedModels())
                .containsExactlyInAnyOrder(
                        new ModelInfo("grok-imagine-image", "grok-imagine-image",
                                List.of(PromptType.IMAGE_GENERATION, PromptType.IMAGE_EDIT)),
                        new ModelInfo("grok-imagine-video", "grok-imagine-video",
                                List.of(PromptType.VIDEO_GENERATION, PromptType.VIDEO_EDIT))
                );
    }

    @Test
    void fetchSupportedModelsThrowsOnHttpError() {
        server.expect(once(), requestTo("https://api.x.ai/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.fetchSupportedModels())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to fetch xAI models");
    }

    @Test
    void fetchSupportedModelsReturnsOnlyStaticModelsWhenDataListIsEmpty() {
        server.expect(once(), requestTo("https://api.x.ai/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": [],
                          "object": "list"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.fetchSupportedModels())
                .containsExactlyInAnyOrder(
                        new ModelInfo("grok-imagine-image", "grok-imagine-image",
                                List.of(PromptType.IMAGE_GENERATION, PromptType.IMAGE_EDIT)),
                        new ModelInfo("grok-imagine-video", "grok-imagine-video",
                                List.of(PromptType.VIDEO_GENERATION, PromptType.VIDEO_EDIT))
                );
    }

    @Test
    void fetchSupportedModelsIgnoresNullModelId() {
        server.expect(once(), requestTo("https://api.x.ai/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"id": null, "created": 1755993600, "object": "model", "owned_by": "xai"},
                            {"id": "grok-3", "created": 1743724800, "object": "model", "owned_by": "xai"}
                          ],
                          "object": "list"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.fetchSupportedModels())
                .containsExactlyInAnyOrder(
                        new ModelInfo("grok-3", "grok-3", List.of(PromptType.TEXT)),
                        new ModelInfo("grok-imagine-image", "grok-imagine-image",
                                List.of(PromptType.IMAGE_GENERATION, PromptType.IMAGE_EDIT)),
                        new ModelInfo("grok-imagine-video", "grok-imagine-video",
                                List.of(PromptType.VIDEO_GENERATION, PromptType.VIDEO_EDIT))
                );
    }

    @Test
    void fetchSupportedModelsSelectsAlphabeticallyLastWhenTimestampsAreTied() {
        server.expect(once(), requestTo("https://api.x.ai/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"id": "grok-3-a", "created": 1743724800, "object": "model", "owned_by": "xai"},
                            {"id": "grok-3-b", "created": 1743724800, "object": "model", "owned_by": "xai"}
                          ],
                          "object": "list"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.fetchSupportedModels())
                .containsExactlyInAnyOrder(
                        new ModelInfo("grok-3-b", "grok-3-b", List.of(PromptType.TEXT)),
                        new ModelInfo("grok-imagine-image", "grok-imagine-image",
                                List.of(PromptType.IMAGE_GENERATION, PromptType.IMAGE_EDIT)),
                        new ModelInfo("grok-imagine-video", "grok-imagine-video",
                                List.of(PromptType.VIDEO_GENERATION, PromptType.VIDEO_EDIT))
                );
    }

    // ─── fetchResults ─────────────────────────────────────────────────────────

    @Test
    void fetchResultsAggregatesPaginatedPages() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_123/results?page_size=100"))
                .andExpect(queryParam("page_size", "100"))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {
                              "batch_request_id": "101",
                              "batch_result": {
                                "response": {
                                  "chat_get_completion": {
                                    "choices": [
                                      {"message": {"content": "first"}}
                                    ]
                                  }
                                }
                              }
                            }
                          ],
                          "pagination_token": "next-token"
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_123/results?page_size=100&pagination_token=next-token"))
                .andExpect(queryParam("page_size", "100"))
                .andExpect(queryParam("pagination_token", "next-token"))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {
                              "batch_request_id": "102",
                              "batch_result": {
                                "response": {
                                  "chat_get_completion": {
                                    "choices": [
                                      {"message": {"content": [{"text": "second"}]}}
                                    ]
                                  }
                                }
                              }
                            }
                          ],
                          "pagination_token": null
                        }
                        """, MediaType.APPLICATION_JSON));

        List<BatchPrompt> prompts = List.of(
                BatchPrompt.builder().id(101L).build(),
                BatchPrompt.builder().id(102L).build()
        );

        assertThat(adapter.fetchResults(new ExternalBatchId("batch_123"), prompts))
                .containsEntry(101L, new PromptResult(true, "first", null, null))
                .containsEntry(102L, new PromptResult(true, "second", null, null));
    }

    @Test
    void fetchResultsThrowsOnHttpError() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_123/results?page_size=100"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.fetchResults(new ExternalBatchId("batch_123"), List.of()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to fetch xAI batch results");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private XAIBatchAdapter.XAIResultItem successResult(String batchRequestId, String content) {
        return new XAIBatchAdapter.XAIResultItem(
                batchRequestId,
                new XAIBatchAdapter.XAIBatchResult(
                        new XAIBatchAdapter.XAIResponsePayload(
                                new XAIBatchAdapter.XAIChatCompletion(List.of(
                                        new XAIBatchAdapter.XAIChoice(
                                                new XAIBatchAdapter.XAIMessage(objectMapper.valueToTree(content))
                                        )
                                ))
                        ),
                        null,
                        null,
                        null
                ),
                null
        );
    }

    private XAIBatchAdapter.XAIResultItem failedResult(String batchRequestId, String errorMessage) {
        return new XAIBatchAdapter.XAIResultItem(
                batchRequestId,
                new XAIBatchAdapter.XAIBatchResult(null, null, null, null),
                errorMessage
        );
    }
}
