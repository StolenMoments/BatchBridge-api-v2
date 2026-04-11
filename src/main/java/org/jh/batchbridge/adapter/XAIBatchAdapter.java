package org.jh.batchbridge.adapter;

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jh.batchbridge.config.ModelListProperties;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.ExternalBatchStatus;
import org.jh.batchbridge.dto.response.ModelInfo;
import org.jh.batchbridge.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class XAIBatchAdapter implements BatchApiPort {

    private static final Logger log = LoggerFactory.getLogger(XAIBatchAdapter.class);
    private static final String MODEL_PREFIX = "grok-";
    private static final int RESULTS_PAGE_SIZE = 100;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ModelListProperties modelListProperties;

    @Autowired
    public XAIBatchAdapter(
            @Value("${batch-bridge.api-keys.grok}") String apiKey,
            @Value("${batch-bridge.xai.connect-timeout-ms:10000}") int connectTimeout,
            @Value("${batch-bridge.xai.read-timeout-ms:60000}") int readTimeout,
            ObjectMapper objectMapper,
            ModelListProperties modelListProperties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeout));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.x.ai")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.objectMapper = objectMapper;
        this.modelListProperties = modelListProperties;
    }

    XAIBatchAdapter(RestClient restClient, ObjectMapper objectMapper, ModelListProperties modelListProperties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.modelListProperties = modelListProperties;
    }

    @Override
    public String getSupportedModelPrefix() {
        return MODEL_PREFIX;
    }

    @Override
    public ExternalBatchId submitBatch(BatchSubmitRequest request) {
        try {
            XAIBatchResponse createdBatch = restClient.post()
                    .uri("/v1/batches")
                    .body(Map.of("name", "bb_batch_" + System.currentTimeMillis()))
                    .retrieve()
                    .body(XAIBatchResponse.class);

            if (createdBatch == null || createdBatch.batchId() == null || createdBatch.batchId().isBlank()) {
                throw new ExternalApiException("xAI batch create response does not contain batch_id");
            }

            restClient.post()
                    .uri("/v1/batches/{batchId}/requests", createdBatch.batchId())
                    .body(Map.of("batch_requests", buildBatchRequests(request)))
                    .retrieve()
                    .toBodilessEntity();

            return new ExternalBatchId(createdBatch.batchId());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("xAI API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalApiException("Failed to submit xAI batch: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to submit xAI batch", e);
        }
    }

    @Override
    public BatchStatusResult fetchStatus(ExternalBatchId externalBatchId) {
        try {
            XAIBatchResponse response = restClient.get()
                    .uri("/v1/batches/{batchId}", externalBatchId.value())
                    .retrieve()
                    .body(XAIBatchResponse.class);

            if (response == null) {
                throw new ExternalApiException("xAI batch status response is empty for id: " + externalBatchId.value());
            }

            return new BatchStatusResult(toBatchStatus(response.state()), null);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("xAI API error fetching status: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalApiException("Failed to fetch xAI batch status: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to fetch xAI batch status: " + externalBatchId.value(), e);
        }
    }

    @Override
    public Map<Long, PromptResult> fetchResults(ExternalBatchId externalBatchId, List<BatchPrompt> prompts) {
        Set<Long> expectedPromptIds = (prompts == null || prompts.isEmpty())
                ? Set.of()
                : prompts.stream()
                        .map(BatchPrompt::getId)
                        .filter(Objects::nonNull)
                        .collect(toSet());

        try {
            Map<Long, PromptResult> results = new LinkedHashMap<>();
            String paginationToken = null;

            while (true) {
                String currentPaginationToken = paginationToken;
                XAIResultsPage page = restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/v1/batches/{batchId}/results")
                                    .queryParam("page_size", RESULTS_PAGE_SIZE);
                            if (currentPaginationToken != null) {
                                uriBuilder.queryParam("pagination_token", currentPaginationToken);
                            }
                            return uriBuilder.build(externalBatchId.value());
                        })
                        .retrieve()
                        .body(XAIResultsPage.class);

                if (page == null) {
                    throw new ExternalApiException("xAI batch results response is empty for id: " + externalBatchId.value());
                }

                results.putAll(parseResultsPage(page.results(), expectedPromptIds));
                if (page.paginationToken() == null || page.paginationToken().isBlank()) {
                    return results;
                }
                paginationToken = page.paginationToken();
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("xAI API error fetching results: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalApiException("Failed to fetch xAI batch results: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to fetch xAI batch results: " + externalBatchId.value(), e);
        }
    }

    @Override
    public List<ModelInfo> fetchSupportedModels() {
        if (modelListProperties.getSupportedModels() == null) {
            return List.of();
        }

        return modelListProperties.getSupportedModels()
                .getOrDefault("grok", List.of())
                .stream()
                .map(model -> new ModelInfo(model.id(), model.displayName()))
                .toList();
    }

    List<Map<String, Object>> buildUserInput(BatchSubmitRequest.PromptItem prompt) {
        List<Map<String, Object>> input = new ArrayList<>();

        if (prompt.systemPrompt() != null && !prompt.systemPrompt().isBlank()) {
            input.add(Map.of("role", "system", "content", prompt.systemPrompt()));
        }

        input.add(Map.of("role", "user", "content", buildUserContent(prompt)));
        return input;
    }

    Map<Long, PromptResult> parseResultsPage(List<XAIResultItem> results, Set<Long> expectedPromptIds) {
        if (results == null || results.isEmpty()) {
            return Map.of();
        }

        Map<Long, PromptResult> parsed = new LinkedHashMap<>();
        for (XAIResultItem result : results) {
            if (result == null || result.batchRequestId() == null || result.batchRequestId().isBlank()) {
                continue;
            }

            final Long promptId;
            try {
                promptId = Long.valueOf(result.batchRequestId());
            } catch (NumberFormatException e) {
                log.warn("Skipping xAI result with non-numeric batch_request_id [batchRequestId={}]", result.batchRequestId());
                continue;
            }

            if (!expectedPromptIds.isEmpty() && !expectedPromptIds.contains(promptId)) {
                log.warn("Skipping xAI result for unknown prompt id [promptId={}]", promptId);
                continue;
            }

            if (result.batchResult() == null) {
                log.warn("Skipping xAI result without batch_result [promptId={}]", promptId);
                continue;
            }

            XAIResponsePayload response = result.batchResult().response();
            PromptResult promptResult;
            if (response != null && response.chatGetCompletion() != null) {
                promptResult = new PromptResult(true, extractText(response.chatGetCompletion()), null);
            } else {
                String errorMessage = result.errorMessage();
                if (errorMessage == null || errorMessage.isBlank()) {
                    errorMessage = "Unknown error";
                }
                promptResult = new PromptResult(false, null, errorMessage);
            }

            parsed.put(promptId, promptResult);
        }
        return parsed;
    }

    ExternalBatchStatus toBatchStatus(XAIBatchState state) {
        if (state == null) {
            return ExternalBatchStatus.IN_PROGRESS;
        }

        int pending = zeroIfNull(state.numPending());
        int success = zeroIfNull(state.numSuccess());
        int error = zeroIfNull(state.numError());
        int cancelled = zeroIfNull(state.numCancelled());

        if (pending > 0) {
            return ExternalBatchStatus.IN_PROGRESS;
        }
        if (success > 0) {
            return ExternalBatchStatus.COMPLETED;
        }
        if (error > 0 || cancelled > 0) {
            return ExternalBatchStatus.FAILED;
        }
        return ExternalBatchStatus.IN_PROGRESS;
    }

    private List<Map<String, Object>> buildBatchRequests(BatchSubmitRequest request) {
        return request.prompts().stream()
                .map(prompt -> Map.<String, Object>of(
                        "batch_request_id", String.valueOf(prompt.promptId()),
                        "batch_request", Map.of(
                                "responses", Map.of(
                                        "model", request.model(),
                                        "input", buildUserInput(prompt)
                                )
                        )
                ))
                .toList();
    }

    private String buildUserContent(BatchSubmitRequest.PromptItem prompt) {
        if (prompt.attachments() == null || prompt.attachments().isEmpty()) {
            return prompt.userPrompt();
        }

        StringBuilder userContent = new StringBuilder("<attachments>\n");
        for (BatchSubmitRequest.AttachmentItem attachment : prompt.attachments()) {
            userContent.append("<attachment name=\"")
                    .append(attachment.fileName())
                    .append("\">")
                    .append(attachment.fileContent())
                    .append("</attachment>\n");
        }
        userContent.append("</attachments>\n\n").append(prompt.userPrompt());
        return userContent.toString();
    }

    private String extractText(XAIChatCompletion response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            return "";
        }

        JsonNode content = response.choices().getFirst().message().content();
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            List<String> texts = new ArrayList<>();
            for (JsonNode item : content) {
                String text = item.path("text").asText(null);
                if (text != null && !text.isBlank()) {
                    texts.add(text);
                }
            }
            return String.join("\n", texts);
        }

        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            return content.toString();
        }
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private record XAIBatchResponse(
            @JsonProperty("batch_id") String batchId,
            XAIBatchState state
    ) {
    }

    record XAIBatchState(
            @JsonProperty("num_requests") Integer numRequests,
            @JsonProperty("num_pending") Integer numPending,
            @JsonProperty("num_success") Integer numSuccess,
            @JsonProperty("num_error") Integer numError,
            @JsonProperty("num_cancelled") Integer numCancelled
    ) {
    }

    private record XAIResultsPage(
            List<XAIResultItem> results,
            @JsonProperty("pagination_token") String paginationToken
    ) {
    }

    record XAIResultItem(
            @JsonProperty("batch_request_id") String batchRequestId,
            @JsonProperty("batch_result") XAIBatchResult batchResult,
            @JsonProperty("error_message") String errorMessage
    ) {
    }

    record XAIBatchResult(
            XAIResponsePayload response
    ) {
    }

    record XAIResponsePayload(
            @JsonProperty("chat_get_completion") XAIChatCompletion chatGetCompletion
    ) {
    }

    record XAIChatCompletion(
            List<XAIChoice> choices
    ) {
    }

    record XAIChoice(
            XAIMessage message
    ) {
    }

    record XAIMessage(
            JsonNode content
    ) {
    }
}
