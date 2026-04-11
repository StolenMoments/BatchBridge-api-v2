package org.jh.batchbridge.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.jh.batchbridge.config.ModelListProperties;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.ExternalBatchStatus;
import org.jh.batchbridge.dto.response.ModelInfo;
import org.jh.batchbridge.dto.response.ModelResponse;
import org.jh.batchbridge.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XAIBatchAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer server;
    private XAIBatchAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.x.ai");
        server = MockRestServiceServer.bindTo(builder).build();

        ModelListProperties modelListProperties = new ModelListProperties();
        modelListProperties.setSupportedModels(Map.of(
                "grok", List.of(new ModelResponse("grok-3", "Grok 3"))
        ));

        adapter = new XAIBatchAdapter(builder.build(), objectMapper, modelListProperties);
    }

    @Test
    void buildUserInputIncludesSystemPromptAndAttachments() {
        BatchSubmitRequest.PromptItem prompt = new BatchSubmitRequest.PromptItem(
                1L,
                "system message",
                "user message",
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
                List.of()
        );

        assertThat(adapter.buildUserInput(prompt))
                .containsExactly(Map.of("role", "user", "content", "plain prompt"));
    }

    @Test
    void parseResultsPageParsesSucceededAndFailedResults() {
        Map<Long, PromptResult> results = adapter.parseResultsPage(List.of(
                successResult("101", "hello"),
                failedResult("102", "bad request")
        ), Set.of(101L, 102L));

        assertThat(results).containsEntry(101L, new PromptResult(true, "hello", null));
        assertThat(results).containsEntry(102L, new PromptResult(false, null, "bad request"));
    }

    @Test
    void parseResultsPageSkipsInvalidOrUnknownResults() {
        Map<Long, PromptResult> results = adapter.parseResultsPage(List.of(
                new XAIBatchAdapter.XAIResultItem("abc", new XAIBatchAdapter.XAIBatchResult(null), null),
                new XAIBatchAdapter.XAIResultItem("999", new XAIBatchAdapter.XAIBatchResult(null), "ignored"),
                new XAIBatchAdapter.XAIResultItem("101", null, null),
                failedResult("102", null)
        ), Set.of(101L, 102L));

        assertThat(results).hasSize(1);
        assertThat(results).containsEntry(102L, new PromptResult(false, null, "Unknown error"));
    }

    @Test
    void parseResultsPageAcceptsAllNumericIdsWhenExpectedIdsEmpty() {
        Map<Long, PromptResult> results = adapter.parseResultsPage(List.of(
                successResult("1", "first"),
                failedResult("2", "error")
        ), Set.of());

        assertThat(results).hasSize(2);
    }

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
                        List.of(new BatchSubmitRequest.AttachmentItem("doc.txt", "body"))
                ))
        ));

        assertThat(batchId).isEqualTo(new ExternalBatchId("batch_123"));
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

    @Test
    void fetchSupportedModelsReturnsConfiguredGrokModels() {
        assertThat(adapter.fetchSupportedModels())
                .containsExactly(new ModelInfo("grok-3", "Grok 3"));
    }

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
                .containsEntry(101L, new PromptResult(true, "first", null))
                .containsEntry(102L, new PromptResult(true, "second", null));
    }

    @Test
    void fetchResultsThrowsOnHttpError() {
        server.expect(once(), requestTo("https://api.x.ai/v1/batches/batch_123/results?page_size=100"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.fetchResults(new ExternalBatchId("batch_123"), List.of()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Failed to fetch xAI batch results");
    }

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
                        )
                ),
                null
        );
    }

    private XAIBatchAdapter.XAIResultItem failedResult(String batchRequestId, String errorMessage) {
        return new XAIBatchAdapter.XAIResultItem(
                batchRequestId,
                new XAIBatchAdapter.XAIBatchResult(null),
                errorMessage
        );
    }
}
