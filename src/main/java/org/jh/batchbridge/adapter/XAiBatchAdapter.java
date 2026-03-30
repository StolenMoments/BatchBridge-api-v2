package org.jh.batchbridge.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.ExternalBatchStatus;
import org.jh.batchbridge.dto.external.xai.XAiBatchResponse;
import org.jh.batchbridge.dto.external.xai.XAiBatchResultsResponse;
import org.jh.batchbridge.dto.external.xai.XAiModelsResponse;
import org.jh.batchbridge.dto.response.ModelInfo;
import org.jh.batchbridge.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class XAiBatchAdapter implements BatchApiPort {

    private static final Logger log = LoggerFactory.getLogger(XAiBatchAdapter.class);
    private static final String MODEL_PREFIX = "grok-";

    private final RestClient restClient;

    public XAiBatchAdapter(
            @Value("${batch-bridge.api-keys.xai}") String apiKey,
            @Value("${batch-bridge.xai.base-url:https://api.x.ai}") String baseUrl,
            @Value("${batch-bridge.xai.connect-timeout-ms:10000}") int connectTimeout,
            @Value("${batch-bridge.xai.read-timeout-ms:30000}") int readTimeout
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeout));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String getSupportedModelPrefix() {
        return MODEL_PREFIX;
    }

    @Override
    public ExternalBatchId submitBatch(BatchSubmitRequest request) {
        // Step 1: Create batch
        String batchId = createBatch(request.label());

        // Step 2: Add requests to batch
        addRequestsToBatch(batchId, request);

        return new ExternalBatchId(batchId);
    }

    String createBatch(String name) {
        try {
            XAiBatchResponse response = restClient.post()
                    .uri("/v1/batches")
                    .body(Map.of("name", name))
                    .retrieve()
                    .body(XAiBatchResponse.class);

            if (response == null || response.batchId() == null) {
                throw new ExternalApiException("xAI batch create response does not contain batch_id");
            }
            return response.batchId();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("xAI API error creating batch: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalApiException("Failed to create xAI batch: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to create xAI batch", e);
        }
    }

    void addRequestsToBatch(String batchId, BatchSubmitRequest request) {
        List<Map<String, Object>> batchRequests = buildBatchRequests(request);

        try {
            restClient.post()
                    .uri("/v1/batches/{id}/requests", batchId)
                    .body(Map.of("batch_requests", batchRequests))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("xAI API error adding requests to batch: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalApiException("Failed to add requests to xAI batch: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to add requests to xAI batch: " + batchId, e);
        }
    }

    List<Map<String, Object>> buildBatchRequests(BatchSubmitRequest request) {
        return request.prompts().stream()
                .map(prompt -> {
                    List<Map<String, String>> messageList = new ArrayList<>();

                    if (prompt.systemPrompt() != null && !prompt.systemPrompt().isBlank()) {
                        messageList.add(Map.of("role", "system", "content", prompt.systemPrompt()));
                    }
                    messageList.add(Map.of("role", "user", "content", prompt.userPrompt()));

                    Map<String, Object> chatGetCompletion = new HashMap<>();
                    chatGetCompletion.put("model", request.model());
                    chatGetCompletion.put("messages", messageList);

                    Map<String, Object> batchRequest = new HashMap<>();
                    batchRequest.put("chat_get_completion", chatGetCompletion);

                    Map<String, Object> item = new HashMap<>();
                    item.put("batch_request_id", String.valueOf(prompt.promptId()));
                    item.put("batch_request", batchRequest);

                    return item;
                })
                .toList();
    }

    @Override
    public BatchStatusResult fetchStatus(ExternalBatchId externalBatchId) {
        try {
            XAiBatchResponse response = restClient.get()
                    .uri("/v1/batches/{id}", externalBatchId.value())
                    .retrieve()
                    .body(XAiBatchResponse.class);

            if (response == null || response.state() == null) {
                throw new ExternalApiException("xAI batch status response is empty for id: " + externalBatchId.value());
            }

            ExternalBatchStatus status = response.state().numPending() == 0 
                    ? ExternalBatchStatus.COMPLETED 
                    : ExternalBatchStatus.IN_PROGRESS;

            return new BatchStatusResult(status, null);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("xAI API error fetching status: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalApiException("Failed to fetch xAI batch status: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to fetch xAI batch status: " + externalBatchId.value(), e);
        }
    }

    @Override
    public Map<Long, PromptResult> fetchResults(ExternalBatchId externalBatchId, List<BatchPrompt> prompts) {
        Map<Long, PromptResult> results = new HashMap<>();
        String nextToken = null;

        do {
            try {
                String uri = "/v1/batches/{id}/results?page_size=100";
                if (nextToken != null) {
                    uri += "&pagination_token=" + nextToken;
                }

                XAiBatchResultsResponse response = restClient.get()
                        .uri(uri, externalBatchId.value())
                        .retrieve()
                        .body(XAiBatchResultsResponse.class);

                if (response == null || response.results() == null) {
                    break;
                }

                for (XAiBatchResultsResponse.XAiBatchResultItem item : response.results()) {
                    if (item.batchRequestId() == null) {
                        log.warn("xAI result item missing batch_request_id, skipping");
                        continue;
                    }
                    
                    Long promptId;
                    try {
                        promptId = Long.valueOf(item.batchRequestId());
                    } catch (NumberFormatException e) {
                        log.warn("xAI result item has invalid batch_request_id: {}, skipping", item.batchRequestId());
                        continue;
                    }

                    PromptResult promptResult;
                    if (item.errorMessage() != null && !item.errorMessage().isBlank()) {
                        promptResult = new PromptResult(false, null, item.errorMessage());
                    } else if (item.batchResult() != null && 
                               item.batchResult().response() != null && 
                               item.batchResult().response().chatGetCompletion() != null && 
                               !item.batchResult().response().chatGetCompletion().choices().isEmpty()) {
                        String content = item.batchResult().response().chatGetCompletion().choices().get(0).message().content();
                        promptResult = new PromptResult(true, content, null);
                    } else {
                        log.warn("xAI result item for prompt {} has neither error nor valid response", promptId);
                        promptResult = new PromptResult(false, null, "Unknown error from xAI");
                    }
                    results.put(promptId, promptResult);
                }

                nextToken = response.paginationToken();
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                log.error("xAI API error fetching results: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new ExternalApiException("Failed to fetch xAI batch results: " + e.getResponseBodyAsString(), e);
            } catch (RestClientException e) {
                throw new ExternalApiException("Failed to fetch xAI batch results: " + externalBatchId.value(), e);
            }
        } while (nextToken != null && !nextToken.isBlank());

        return results;
    }

    @Override
    public List<ModelInfo> fetchSupportedModels() {
        try {
            XAiModelsResponse response = restClient.get()
                    .uri("/v1/models")
                    .retrieve()
                    .body(XAiModelsResponse.class);

            if (response == null || response.models() == null) {
                return List.of();
            }

            return response.models().stream()
                    .filter(m -> m.id().startsWith(MODEL_PREFIX))
                    .map(m -> new ModelInfo(m.id(), m.name()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch xAI models: {}", e.getMessage());
            // It's better not to throw exception here to avoid breaking model list page if one provider is down
            return List.of();
        }
    }
}
