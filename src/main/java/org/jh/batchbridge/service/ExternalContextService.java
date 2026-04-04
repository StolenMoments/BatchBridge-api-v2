package org.jh.batchbridge.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jh.batchbridge.dto.request.external.GithubPreviewRequest;
import org.jh.batchbridge.dto.request.external.JiraPreviewRequest;
import org.jh.batchbridge.dto.response.external.ExternalContextPreviewResponse;
import org.jh.batchbridge.exception.ExternalApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class ExternalContextService {

    private final RestClient githubRestClient;
    private final RestClient atlassianRestClient;

    public ExternalContextService(
            @Nullable @Qualifier("githubRestClient") RestClient githubRestClient,
            @Nullable @Qualifier("atlassianRestClient") RestClient atlassianRestClient) {
        this.githubRestClient = githubRestClient;
        this.atlassianRestClient = atlassianRestClient;
    }

    public ExternalContextPreviewResponse previewGithub(GithubPreviewRequest request) {
        if (githubRestClient == null) {
            throw new ExternalApiException("GitHub client is not configured. Please provide 'external-context.github-token'.");
        }

        try {
            Map<String, Object> response = githubRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/contents/{path}")
                            .queryParam("ref", request.ref())
                            .build(request.owner(), request.repo(), request.path()))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("content")) {
                throw new ExternalApiException("GitHub response does not contain content.");
            }

            String base64Content = (String) response.get("content");
            String decoded = new String(Base64.getMimeDecoder().decode(base64Content.replaceAll("\n", "")), StandardCharsets.UTF_8);

            return new ExternalContextPreviewResponse(
                    String.format("GitHub: %s/%s/%s", request.owner(), request.repo(), request.path()),
                    decoded
            );
        } catch (Exception e) {
            log.error("Failed to fetch content from GitHub", e);
            throw new ExternalApiException("Failed to fetch content from GitHub: " + e.getMessage(), e);
        }
    }

    public ExternalContextPreviewResponse previewJira(JiraPreviewRequest request) {
        if (atlassianRestClient == null) {
            throw new ExternalApiException("Atlassian client is not configured. Please provide 'external-context.atlassian-*' properties.");
        }

        try {
            Map<String, Object> response = atlassianRestClient.get()
                    .uri("/rest/api/3/issue/{issueKey}", request.issueKey())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("fields")) {
                throw new ExternalApiException("JIRA response does not contain fields.");
            }

            Map<String, Object> fields = (Map<String, Object>) response.get("fields");
            String summary = (String) fields.get("summary");
            Object descriptionObj = fields.get("description");

            // For simplicity, we just show the summary and a placeholder for description if it's ADF
            // In a real implementation, we would convert ADF to Markdown or plain text.
            String content = String.format("Summary: %s\n\nIssue Key: %s", summary, request.issueKey());
            if (descriptionObj != null) {
                 content += "\n(Description available in ADF format)";
            }

            return new ExternalContextPreviewResponse(
                    String.format("JIRA: %s", request.issueKey()),
                    content
            );
        } catch (Exception e) {
            log.error("Failed to fetch issue from JIRA", e);
            throw new ExternalApiException("Failed to fetch issue from JIRA: " + e.getMessage(), e);
        }
    }
}
