package org.jh.batchbridge.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.jh.batchbridge.dto.external.BatchStatus;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.claude.ClaudeBatchResponse;
import org.jh.batchbridge.exception.ExternalApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ClaudeBatchAdapter implements BatchApiPort {

    private static final String MODEL_PREFIX = "claude-";
    private static final String PROCESSING_STATUS_ENDED = "ended";
    private static final String RESULT_TYPE_SUCCEEDED = "succeeded";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int defaultMaxTokens;

    public ClaudeBatchAdapter(
            @Value("${batch-bridge.api-keys.claude}") String apiKey,
            @Value("${batch-bridge.claude.default-max-tokens:1024}") int defaultMaxTokens
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("anthropic-beta", "message-batches-2024-09-24")
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.objectMapper = new ObjectMapper();
        this.defaultMaxTokens = defaultMaxTokens;
    }

    @Override
    public String getSupportedModelPrefix() {
        return MODEL_PREFIX;
    }

    @Override
    public ExternalBatchId submitBatch(BatchSubmitRequest request) {
        Map<String, Object> requestBody = buildSubmitRequestBody(request);
        try {
            ClaudeBatchResponse response = restClient.post()
                    .uri("/v1/messages/batches")
                    .body(requestBody)
                    .retrieve()
                    .body(ClaudeBatchResponse.class);
            
            if (response == null || response.id() == null || response.id().isBlank()) {
                throw new ExternalApiException("Claude batch create response does not contain id");
            }
            return new ExternalBatchId(response.id());
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to create Claude batch", e);
        }
    }

    @Override
    public BatchStatusResult fetchStatus(ExternalBatchId externalBatchId) {
        try {
            ClaudeBatchResponse response = restClient.get()
                    .uri("/v1/messages/batches/{id}", externalBatchId.value())
                    .retrieve()
                    .body(ClaudeBatchResponse.class);
            
            if (response == null) {
                throw new ExternalApiException("Claude batch status response is empty for id: " + externalBatchId.value());
            }

            String errorMessage = null;
            if (response.error() != null) {
                errorMessage = String.format("[%s] %s", response.error().type(), response.error().message());
            }

            return new BatchStatusResult(toBatchStatus(response.processingStatus()), errorMessage);
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to fetch Claude batch status: " + externalBatchId.value(), e);
        }
    }

    @Override
    public String fetchResult(ExternalBatchId externalBatchId) {
        try {
            String jsonlBody = restClient.get()
                    .uri("/v1/messages/batches/{id}/results", externalBatchId.value())
                    .retrieve()
                    .body(String.class);
            if (jsonlBody == null || jsonlBody.isBlank()) {
                return "";
            }
            return extractSucceededText(jsonlBody);
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to fetch Claude batch result: " + externalBatchId.value(), e);
        }
    }

    private Map<String, Object> buildSubmitRequestBody(BatchSubmitRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", request.model());
        params.put("max_tokens", defaultMaxTokens);

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            params.put("system", request.systemPrompt());
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "user",
                "content", request.userPrompt()
        ));
        params.put("messages", messages);

        Map<String, Object> batchItem = new LinkedHashMap<>();
        batchItem.put("custom_id", request.internalRequestId());
        batchItem.put("params", params);

        return Map.of("requests", List.of(batchItem));
    }

    private BatchStatus toBatchStatus(String processingStatus) {
        if (processingStatus == null || processingStatus.isBlank()) {
            return BatchStatus.IN_PROGRESS;
        }

        String normalized = processingStatus.toLowerCase(Locale.ROOT);
        if (PROCESSING_STATUS_ENDED.equals(normalized)) {
            return BatchStatus.COMPLETED;
        }
        if ("failed".equals(normalized) || "errored".equals(normalized) || "canceled".equals(normalized)) {
            return BatchStatus.FAILED;
        }
        return BatchStatus.IN_PROGRESS;
    }

    private String extractSucceededText(String jsonlBody) {
        List<String> texts = new ArrayList<>();
        String[] lines = jsonlBody.split("\\R");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode lineNode = objectMapper.readTree(line);
                JsonNode resultNode = lineNode.path("result");
                if (!RESULT_TYPE_SUCCEEDED.equalsIgnoreCase(resultNode.path("type").asText())) {
                    continue;
                }
                JsonNode contentNode = resultNode.path("message").path("content");
                if (!contentNode.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : contentNode) {
                    if ("text".equalsIgnoreCase(contentItem.path("type").asText())) {
                        String text = contentItem.path("text").asText(null);
                        if (text != null && !text.isBlank()) {
                            texts.add(text);
                        }
                    }
                }
            } catch (IOException e) {
                throw new ExternalApiException("Failed to parse Claude results jsonl", e);
            }
        }
        return String.join("\n", texts);
    }
}
