package org.jh.batchbridge.service;

import lombok.extern.slf4j.Slf4j;
import org.jh.batchbridge.dto.request.external.ContextPreviewRequest;
import org.jh.batchbridge.dto.response.external.ContextPreviewResponse;
import org.jh.batchbridge.dto.response.external.SourceResult;
import org.jh.batchbridge.dto.response.external.SourceStatus;
import org.jh.batchbridge.dto.response.external.SourceType;
import org.jh.batchbridge.exception.ExternalApiException;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ExternalContextService {

    private static final Pattern GITHUB_PR_URL_PATTERN =
            Pattern.compile("github\\.com/([^/]+)/([^/]+)/pull/(\\d+)");
    private static final int MAX_PATCH_LINES = 200;

    private final RestClient githubRestClient;
    private final RestClient atlassianRestClient;

    public ExternalContextService(
            @Nullable @Qualifier("githubRestClient") RestClient githubRestClient,
            @Nullable @Qualifier("atlassianRestClient") RestClient atlassianRestClient) {
        this.githubRestClient = githubRestClient;
        this.atlassianRestClient = atlassianRestClient;
    }

    public ContextPreviewResponse preview(ContextPreviewRequest request) {
        List<FetchResult> fetchResults = new ArrayList<>();

        if (StringUtils.hasText(request.githubPrUrl())) {
            fetchResults.add(fetchGithubPr(request.githubPrUrl()));
        }
        if (request.jiraKeys() != null) {
            for (String key : request.jiraKeys()) {
                if (StringUtils.hasText(key)) {
                    fetchResults.add(fetchJiraIssue(key));
                }
            }
        }
        if (request.confluencePageIds() != null) {
            for (String pageId : request.confluencePageIds()) {
                if (StringUtils.hasText(pageId)) {
                    fetchResults.add(fetchConfluencePage(pageId));
                }
            }
        }

        if (fetchResults.isEmpty()) {
            throw new IllegalArgumentException("At least one source (githubPrUrl, jiraKeys, confluencePageIds) must be provided.");
        }

        List<SourceResult> sources = fetchResults.stream()
                .map(r -> new SourceResult(
                        r.type(), r.id(), r.title(),
                        r.isSuccess() ? SourceStatus.SUCCESS : SourceStatus.FAILED,
                        r.error()))
                .toList();

        long successCount = sources.stream().filter(s -> s.status() == SourceStatus.SUCCESS).count();
        if (successCount == 0) {
            throw new ExternalApiException("All context sources failed to fetch.");
        }

        String contextText = buildContextText(fetchResults);
        return new ContextPreviewResponse(contextText, sources, null);
    }

    // -------------------------------------------------------------------------
    // GitHub PR
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private FetchResult fetchGithubPr(String prUrl) {
        if (githubRestClient == null) {
            return FetchResult.failure(SourceType.GITHUB_PR, prUrl, "GitHub client is not configured.");
        }
        try {
            Matcher matcher = GITHUB_PR_URL_PATTERN.matcher(prUrl);
            if (!matcher.find()) {
                return FetchResult.failure(SourceType.GITHUB_PR, prUrl, "Invalid GitHub PR URL format.");
            }
            String owner = matcher.group(1);
            String repo = matcher.group(2);
            String number = matcher.group(3);

            Map<String, Object> pr = githubRestClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, number)
                    .retrieve()
                    .body(Map.class);

            if (pr == null) {
                return FetchResult.failure(SourceType.GITHUB_PR, number, "GitHub PR not found.");
            }

            String title = pr.get("title") instanceof String s ? s : "";
            String body = pr.get("body") instanceof String s ? s : "";

            List<Map<String, Object>> files = githubRestClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}/files", owner, repo, number)
                    .retrieve()
                    .body(List.class);

            String contextBlock = buildGithubPrBlock(number, title, body, files);
            return FetchResult.success(SourceType.GITHUB_PR, number, title, contextBlock);
        } catch (Exception e) {
            log.error("Failed to fetch GitHub PR: {}", prUrl, e);
            return FetchResult.failure(SourceType.GITHUB_PR, prUrl, e.getMessage());
        }
    }

    private String buildGithubPrBlock(String number, String title, String body, @Nullable List<Map<String, Object>> files) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[GitHub PR] #%s: %s%n", number, title));
        if (StringUtils.hasText(body)) {
            sb.append(body).append("\n");
        }
        sb.append("\n## Changed Files\n");
        if (files != null) {
            for (Map<String, Object> file : files) {
                String filename = String.valueOf(file.getOrDefault("filename", ""));
                int additions = ((Number) file.getOrDefault("additions", 0)).intValue();
                int deletions = ((Number) file.getOrDefault("deletions", 0)).intValue();
                sb.append(String.format("- %s (+%d / -%d)%n", filename, additions, deletions));
                if (file.get("patch") instanceof String patch) {
                    sb.append(truncateToLines(patch, MAX_PATCH_LINES)).append("\n");
                }
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Jira
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private FetchResult fetchJiraIssue(String issueKey) {
        if (atlassianRestClient == null) {
            return FetchResult.failure(SourceType.JIRA, issueKey, "Atlassian client is not configured.");
        }
        try {
            Map<String, Object> response = atlassianRestClient.get()
                    .uri("/rest/api/3/issue/{issueKey}", issueKey)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("fields") instanceof Map)) {
                return FetchResult.failure(SourceType.JIRA, issueKey, "Invalid Jira response.");
            }

            Map<String, Object> fields = (Map<String, Object>) response.get("fields");
            String summary = fields.get("summary") instanceof String s ? s : "";

            String status = fields.get("status") instanceof Map<?, ?> statusMap && statusMap.get("name") != null
                    ? String.valueOf(statusMap.get("name"))
                    : "Unknown";

            String issueType = fields.get("issuetype") instanceof Map<?, ?> typeMap && typeMap.get("name") != null
                    ? String.valueOf(typeMap.get("name"))
                    : "Unknown";

            String description = adfToPlainText(fields.get("description"));

            String contextBlock = buildJiraBlock(issueKey, summary, status, issueType, description);
            return FetchResult.success(SourceType.JIRA, issueKey, summary, contextBlock);
        } catch (Exception e) {
            log.error("Failed to fetch Jira issue: {}", issueKey, e);
            return FetchResult.failure(SourceType.JIRA, issueKey, e.getMessage());
        }
    }

    private String buildJiraBlock(String issueKey, String summary, String status, String issueType, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[Jira] %s: %s%n", issueKey, summary));
        sb.append(String.format("Status: %s%n", status));
        sb.append(String.format("Type: %s%n", issueType));
        if (StringUtils.hasText(description)) {
            sb.append(description.trim()).append("\n");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Confluence
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private FetchResult fetchConfluencePage(String pageId) {
        if (atlassianRestClient == null) {
            return FetchResult.failure(SourceType.CONFLUENCE, pageId, "Atlassian client is not configured.");
        }
        try {
            Map<String, Object> response = atlassianRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/wiki/api/v2/pages/{pageId}")
                            .queryParam("body-format", "storage")
                            .build(pageId))
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return FetchResult.failure(SourceType.CONFLUENCE, pageId, "Confluence page not found.");
            }

            String title = response.get("title") instanceof String s ? s : pageId;

            String htmlBody = "";
            if (response.get("body") instanceof Map<?, ?> bodyMap
                    && bodyMap.get("storage") instanceof Map<?, ?> storageMap
                    && storageMap.get("value") instanceof String value) {
                htmlBody = value;
            }

            String plainText = Jsoup.parse(htmlBody).text();
            String contextBlock = buildConfluenceBlock(title, plainText);
            return FetchResult.success(SourceType.CONFLUENCE, pageId, title, contextBlock);
        } catch (Exception e) {
            log.error("Failed to fetch Confluence page: {}", pageId, e);
            return FetchResult.failure(SourceType.CONFLUENCE, pageId, e.getMessage());
        }
    }

    private String buildConfluenceBlock(String title, String plainText) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[Confluence] %s%n", title));
        if (StringUtils.hasText(plainText)) {
            sb.append(plainText).append("\n");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildContextText(List<FetchResult> results) {
        StringBuilder sb = new StringBuilder("--- context ---\n");
        for (FetchResult r : results) {
            if (r.isSuccess()) {
                sb.append(r.contextBlock()).append("\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    @SuppressWarnings("unchecked")
    private String adfToPlainText(Object node) {
        if (!(node instanceof Map<?, ?> map)) return "";
        String type = map.get("type") instanceof String s ? s : "";

        if ("text".equals(type) && map.get("text") instanceof String text) {
            return text;
        }

        if (!(map.get("content") instanceof List<?> content)) return "";

        StringBuilder sb = new StringBuilder();
        for (Object child : content) {
            sb.append(adfToPlainText(child));
        }

        if ("paragraph".equals(type) || "heading".equals(type)
                || "listItem".equals(type) || "blockquote".equals(type)
                || "codeBlock".equals(type)) {
            sb.append("\n");
        }

        return sb.toString();
    }

    private String truncateToLines(String text, int maxLines) {
        String[] lines = text.split("\n", maxLines + 1);
        if (lines.length <= maxLines) return text;
        return String.join("\n", Arrays.copyOf(lines, maxLines)) + "\n... (truncated)";
    }

    // -------------------------------------------------------------------------
    // Internal result type
    // -------------------------------------------------------------------------

    private record FetchResult(
            SourceType type,
            String id,
            @Nullable String title,
            @Nullable String contextBlock,
            @Nullable String error
    ) {
        boolean isSuccess() {
            return error == null;
        }

        static FetchResult success(SourceType type, String id, String title, String contextBlock) {
            return new FetchResult(type, id, title, contextBlock, null);
        }

        static FetchResult failure(SourceType type, String id, String error) {
            return new FetchResult(type, id, null, null, error);
        }
    }
}
