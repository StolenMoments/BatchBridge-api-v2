package org.jh.batchbridge.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExternalContextProperties.class)
public class ExternalContextClientConfig {

    @Bean("githubRestClient")
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${external-context.github-token:}')")
    public RestClient githubRestClient(ExternalContextProperties properties) {
        return RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + properties.getGithubToken())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean("atlassianRestClient")
    @ConditionalOnExpression(
            "T(org.springframework.util.StringUtils).hasText('${external-context.atlassian-base-url:}') && "
                    + "T(org.springframework.util.StringUtils).hasText('${external-context.atlassian-email:}') && "
                    + "T(org.springframework.util.StringUtils).hasText('${external-context.atlassian-api-token:}')"
    )
    public RestClient atlassianRestClient(ExternalContextProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getAtlassianBaseUrl())
                .defaultHeader("Authorization", "Basic " + basicAuthToken(properties))
                .defaultHeader("Accept", "application/json")
                .build();
    }

    private String basicAuthToken(ExternalContextProperties properties) {
        String credentials = properties.getAtlassianEmail() + ":" + properties.getAtlassianApiToken();
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
