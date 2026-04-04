package org.jh.batchbridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external-context")
public class ExternalContextProperties {

    private String githubToken;
    private String atlassianBaseUrl;
    private String atlassianEmail;
    private String atlassianApiToken;

    public String getGithubToken() {
        return githubToken;
    }

    public void setGithubToken(String githubToken) {
        this.githubToken = githubToken;
    }

    public String getAtlassianBaseUrl() {
        return atlassianBaseUrl;
    }

    public void setAtlassianBaseUrl(String atlassianBaseUrl) {
        this.atlassianBaseUrl = atlassianBaseUrl;
    }

    public String getAtlassianEmail() {
        return atlassianEmail;
    }

    public void setAtlassianEmail(String atlassianEmail) {
        this.atlassianEmail = atlassianEmail;
    }

    public String getAtlassianApiToken() {
        return atlassianApiToken;
    }

    public void setAtlassianApiToken(String atlassianApiToken) {
        this.atlassianApiToken = atlassianApiToken;
    }
}
