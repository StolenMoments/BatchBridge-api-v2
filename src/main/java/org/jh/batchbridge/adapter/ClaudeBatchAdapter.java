package org.jh.batchbridge.adapter;

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jh.batchbridge.domain.BatchPrompt;
import org.jh.batchbridge.domain.PromptResult;
import org.jh.batchbridge.dto.external.BatchStatusResult;
import org.jh.batchbridge.dto.external.BatchSubmitRequest;
import org.jh.batchbridge.dto.external.ExternalBatchId;
import org.jh.batchbridge.dto.external.ExternalBatchStatus;
import org.jh.batchbridge.dto.external.claude.ClaudeBatchResponse;
import org.jh.batchbridge.dto.response.ModelInfo;
import org.jh.batchbridge.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ClaudeBatchAdapter implements BatchApiPort {

    private static final Logger log = LoggerFactory.getLogger(ClaudeBatchAdapter.class);
    private static final String MODEL_PREFIX = "claude-";
    private static final String PROCESSING_STATUS_ENDED = "ended";
    private static final String PROCESSING_STATUS_CANCELING = "canceling";
    private static final String PROCESSING_STATUS_CANCELED = "canceled";
    private static final String PROCESSING_STATUS_EXPIRED = "expired";
    private static final String RESULT_TYPE_SUCCEEDED = "succeeded";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int defaultMaxTokens;

    @org.springframework.beans.factory.annotation.Autowired
    public ClaudeBatchAdapter(
            @Value("${batch-bridge.api-keys.claude}") String apiKey,
            @Value("${batch-bridge.claude.beta-header:message-batches-2024-09-24}") String betaHeader,
            @Value("${batch-bridge.claude.connect-timeout-ms:10000}") int connectTimeout,
            @Value("${batch-bridge.claude.read-timeout-ms:30000}") int readTimeout,
            @Value("${batch-bridge.claude.default-max-tokens:63000}") int defaultMaxTokens,
            ObjectMapper objectMapper
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeout));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("anthropic-beta", betaHeader)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.objectMapper = objectMapper;
        this.defaultMaxTokens = defaultMaxTokens;
    }

    /** 테스트용 생성자 — 외부에서 RestClient를 주입받아 HTTP 호출 없이 단위 테스트 가능. */
    ClaudeBatchAdapter(RestClient restClient, int defaultMaxTokens, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
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
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Claude API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalApiException("Failed to create Claude batch: " + e.getResponseBodyAsString(), e);
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
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Claude API error fetching status: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalApiException("Failed to fetch Claude batch status: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to fetch Claude batch status: " + externalBatchId.value(), e);
        }
    }

    @Override
    public List<ModelInfo> fetchSupportedModels() {
        try {
            ClaudeModelsResponse response = restClient.get()
                    .uri("/v1/models?limit=100")
                    .retrieve()
                    .body(ClaudeModelsResponse.class);

            if (response == null || response.data() == null) {
                return List.of();
            }

            // Filter for only the latest versions:
            // 1. Group by base name (without date suffix if possible)
            // 2. Sort by id (lexicographical since it's date-based YYYYMMDD)
            // 3. Keep only the last one for each group
            Map<String, ModelInfo> latestModels = new HashMap<>();
            for (ClaudeModelData data : response.data()) {
                String id = data.id();
                String baseName = getBaseName(id);
                
                ModelInfo current = new ModelInfo(id, data.displayName());
                ModelInfo existing = latestModels.get(baseName);
                
                if (existing == null || id.compareTo(existing.id()) > 0) {
                    latestModels.put(baseName, current);
                }
            }

            return new ArrayList<>(latestModels.values());
        } catch (Exception e) {
            log.error("Failed to fetch Claude models: {}", e.getMessage());
            throw new ExternalApiException("Failed to fetch Claude models", e);
        }
    }

    /**
     * Identifies the base name of a Claude model by stripping the date suffix if present.
     * Declared as package-private for testing purposes.
     */
    String getBaseName(String modelId) {
        // First, handle -latest
        String base = modelId;
        if (base.endsWith("-latest")) {
            base = base.substring(0, base.length() - "-latest".length());
        }

        // Then, handle date suffix -YYYYMMDD
        if (base.matches(".*-\\d{8}")) {
            base = base.substring(0, base.lastIndexOf('-'));
        }

        // The user seems to want to group all variants of a model family together
        // Example: claude-sonnet-4-5, claude-sonnet-4-6 -> claude-sonnet
        // If we strip ALL trailing version-like numbers/dots, we can group them.
        
        // But let's look at the example again:
        // claude-sonnet-4-5-20250929 -> base "claude-sonnet-4-5"
        // claude-sonnet-4-6 -> base "claude-sonnet-4-6"
        // They are different bases, so both show up.
        
        // If the user says "걸러내야해" (must filter out) for these, 
        // they probably want only the latest SONNET, latest OPUS, etc. regardless of minor version numbers in the ID.
        
        // New strategy: find the family name (sonnet, opus, haiku) and everything before it.
        // ID pattern usually is: claude-[version]-[family]-[subversion]
        // Actually, it varies. "claude-3-5-sonnet", "claude-sonnet-4-5"
        
        // Let's try to identify the family keywords.
        if (base.contains("sonnet")) {
            return base.substring(0, base.indexOf("sonnet") + "sonnet".length());
        }
        if (base.contains("opus")) {
            return base.substring(0, base.indexOf("opus") + "opus".length());
        }
        if (base.contains("haiku")) {
            return base.substring(0, base.indexOf("haiku") + "haiku".length());
        }
        
        return base;
    }

    private record ClaudeModelsResponse(List<ClaudeModelData> data) {}
    private record ClaudeModelData(String id, @JsonProperty("display_name") String displayName) {}

    @Override
    public Map<Long, PromptResult> fetchResults(ExternalBatchId externalBatchId, List<BatchPrompt> prompts) {
        Set<Long> expectedPromptIds = (prompts == null || prompts.isEmpty())
                ? Set.of()
                : prompts.stream()
                        .map(BatchPrompt::getId)
                        .filter(Objects::nonNull)
                        .collect(toSet());

        try {
            return restClient.get()
                    .uri("/v1/messages/batches/{id}/results", externalBatchId.value())
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new ExternalApiException("Failed to fetch Claude batch results: " + response.getStatusCode());
                        }
                        return parseResultsFromStream(response, expectedPromptIds);
                    });
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Claude API error fetching results: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalApiException("Failed to fetch Claude batch results: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalApiException("Failed to fetch Claude batch results: " + externalBatchId.value(), e);
        }
    }

    /** 테스트용으로 package-private 선언. */
    Map<Long, PromptResult> parseResultsFromStream(ClientHttpResponse response, Set<Long> expectedPromptIds) throws IOException {
        Map<Long, PromptResult> results = new HashMap<>();
        int skippedLines = 0;
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
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
                    JsonNode errorNode = resultNode.path("error");
                    String errorMsg = errorNode.path("message").asText(null);

                    // Handle nested error structure: result.error.error.message
                    if (errorMsg == null && errorNode.has("error")) {
                        errorMsg = errorNode.path("error").path("message").asText(null);
                    }

                    if (errorMsg == null) {
                        errorMsg = "Unknown error";
                    }
                    promptResult = new PromptResult(false, null, errorMsg);
                }

                PromptResult previous = results.put(promptId, promptResult);
                if (previous != null) {
                    log.warn("Duplicate Claude result detected for prompt id [promptId={}]", promptId);
                }
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

    /**
     * Builds the request body for Claude batch submission.
     * Declared as package-private for testing purposes.
     */
    Map<String, Object> buildSubmitRequestBody(BatchSubmitRequest request) {
        List<Map<String, Object>> requests = request.prompts().stream()
                .map(prompt -> {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("model", request.model());
                    params.put("max_tokens", defaultMaxTokens);

                    if (prompt.systemPrompt() != null && !prompt.systemPrompt().isBlank()) {
                        params.put("system", prompt.systemPrompt());
                    }

                    StringBuilder userContent = new StringBuilder();
                    if (prompt.attachments() != null && !prompt.attachments().isEmpty()) {
                        userContent.append("<attachments>\n");
                        for (BatchSubmitRequest.AttachmentItem attachment : prompt.attachments()) {
                            userContent.append("<attachment name=\"")
                                    .append(escapeXml(attachment.fileName()))
                                    .append("\">\n")
                                    .append(escapeXml(attachment.fileContent()))
                                    .append("\n</attachment>\n");
                        }
                        userContent.append("</attachments>\n\n");
                    }
                    userContent.append(prompt.userPrompt());

                    List<Map<String, Object>> messages = new ArrayList<>();
                    messages.add(Map.of(
                            "role", "user",
                            "content", userContent.toString()
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

    private ExternalBatchStatus toBatchStatus(String processingStatus) {
        if (processingStatus == null || processingStatus.isBlank()) {
            return ExternalBatchStatus.IN_PROGRESS;
        }

        String normalized = processingStatus.toLowerCase(Locale.ROOT);
        if (PROCESSING_STATUS_ENDED.equals(normalized)) {
            return ExternalBatchStatus.COMPLETED;
        }
        if ("failed".equals(normalized) || "errored".equals(normalized) 
                || PROCESSING_STATUS_CANCELING.equals(normalized)
                || PROCESSING_STATUS_CANCELED.equals(normalized)
                || PROCESSING_STATUS_EXPIRED.equals(normalized)) {
            return ExternalBatchStatus.FAILED;
        }
        return ExternalBatchStatus.IN_PROGRESS;
    }

    /**
     * Parse JSONL results from Claude API.
     * Declared as package-private for testing purposes.
     */
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
                JsonNode errorNode = resultNode.path("error");
                String errorMsg = errorNode.path("message").asText(null);
                
                // Handle nested error structure: result.error.error.message
                if (errorMsg == null && errorNode.has("error")) {
                    errorMsg = errorNode.path("error").path("message").asText(null);
                }
                
                if (errorMsg == null) {
                    errorMsg = "Unknown error";
                }
                
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

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
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
