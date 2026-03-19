package org.jh.batchbridge.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchStatus;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.claude.ClaudeBatchResponse;
import org.jh.batchbridge.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ClaudeBatchAdapter implements BatchApiPort {

    private static final Logger log = LoggerFactory.getLogger(ClaudeBatchAdapter.class);
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
    public Map<Long, PromptResult> fetchResults(ExternalBatchId externalBatchId, List<BatchPrompt> prompts) {
        try {
            String jsonlBody = restClient.get()
                    .uri("/v1/messages/batches/{id}/results", externalBatchId.value())
                    .retrieve()
                    .body(String.class);
            if (jsonlBody == null || jsonlBody.isBlank()) {
                return Map.of();
            }
            Set<Long> expectedPromptIds = prompts == null
                    ? Set.of()
                    : prompts.stream()
                            .map(BatchPrompt::getId)
                            .filter(Objects::nonNull)
                            .collect(java.util.stream.Collectors.toSet());
            return parseResults(jsonlBody, expectedPromptIds);
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to fetch Claude batch results: " + externalBatchId.value(), e);
        }
    }

    private Map<String, Object> buildSubmitRequestBody(BatchSubmitRequest request) {
        List<Map<String, Object>> requests = request.prompts().stream()
                .map(prompt -> {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("model", request.model());
                    params.put("max_tokens", defaultMaxTokens);

                    if (prompt.systemPrompt() != null && !prompt.systemPrompt().isBlank()) {
                        params.put("system", prompt.systemPrompt());
                    }

                    List<Map<String, Object>> messages = new ArrayList<>();
                    messages.add(Map.of(
                            "role", "user",
                            "content", prompt.userPrompt()
                    ));
                    params.put("messages", messages);

                    Map<String, Object> batchItem = new LinkedHashMap<>();
                    batchItem.put("custom_id", String.valueOf(prompt.promptId()));
                    batchItem.put("params", params);

                    return batchItem;
                })
                .toList();

        return Map.of("requests", requests);
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

    Map<Long, PromptResult> parseResults(String jsonlBody, Set<Long> expectedPromptIds) {
        Map<Long, PromptResult> results = new HashMap<>();
        String[] lines = jsonlBody.split("\\R");
        int skippedLines = 0;
        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            JsonNode lineNode;
            try {
                lineNode = objectMapper.readTree(line);
            } catch (IOException e) {
                skippedLines++;
                log.warn("Skipping invalid Claude result line [line={}]", lineNumber, e);
                continue;
            }

            String customId = lineNode.path("custom_id").asText(null);
            if (customId == null || customId.isBlank()) {
                skippedLines++;
                log.warn("Skipping Claude result line without custom_id [line={}]", lineNumber);
                continue;
            }

            final Long promptId;
            try {
                promptId = Long.valueOf(customId);
            } catch (NumberFormatException e) {
                skippedLines++;
                log.warn("Skipping Claude result line with non-numeric custom_id [line={}, customId={}]", lineNumber, customId);
                continue;
            }

            if (!expectedPromptIds.isEmpty() && !expectedPromptIds.contains(promptId)) {
                skippedLines++;
                log.warn("Skipping Claude result for unknown prompt id [promptId={}]", promptId);
                continue;
            }

            JsonNode resultNode = lineNode.path("result");
            String type = resultNode.path("type").asText();

            PromptResult promptResult;
            if (RESULT_TYPE_SUCCEEDED.equalsIgnoreCase(type)) {
                String text = extractText(resultNode.path("message").path("content"));
                promptResult = new PromptResult(true, text, null);
            } else {
                String errorMsg = resultNode.path("error").path("message").asText("Unknown error");
                promptResult = new PromptResult(false, null, errorMsg);
            }

            PromptResult previous = results.put(promptId, promptResult);
            if (previous != null) {
                log.warn("Duplicate Claude result detected for prompt id [promptId={}]", promptId);
            }
        }

        if (skippedLines > 0) {
            log.warn("Skipped {} invalid/unmatched Claude result line(s)", skippedLines);
        }
        if (!expectedPromptIds.isEmpty() && results.isEmpty()) {
            log.warn("No valid Claude prompt results parsed from payload");
        }
        if (!expectedPromptIds.isEmpty() && results.size() < expectedPromptIds.size()) {
            log.warn("Claude results are missing prompts [expected={}, parsed={}]", expectedPromptIds.size(), results.size());
        }

        return results;
    }

    private String extractText(JsonNode contentNode) {
        if (!contentNode.isArray()) {
            return "";
        }
        List<String> texts = new ArrayList<>();
        for (JsonNode contentItem : contentNode) {
            if ("text".equalsIgnoreCase(contentItem.path("type").asText())) {
                String text = contentItem.path("text").asText(null);
                if (text != null && !text.isBlank()) {
                    texts.add(text);
                }
            }
        }
        return String.join("\n", texts);
    }
}
