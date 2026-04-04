package org.jh.batchbridge.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class ExternalContextClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ExternalContextClientConfig.class);

    @Test
    void bindsExternalContextPropertiesFromConfiguration() {
        contextRunner
                .withPropertyValues(
                        "external-context.github-token=github-token",
                        "external-context.atlassian-base-url=https://stolenmoments.atlassian.net",
                        "external-context.atlassian-email=batchbridge@example.com",
                        "external-context.atlassian-api-token=api-token"
                )
                .run(context -> {
                    ExternalContextProperties properties = context.getBean(ExternalContextProperties.class);

                    assertThat(properties.getGithubToken()).isEqualTo("github-token");
                    assertThat(properties.getAtlassianBaseUrl()).isEqualTo("https://stolenmoments.atlassian.net");
                    assertThat(properties.getAtlassianEmail()).isEqualTo("batchbridge@example.com");
                    assertThat(properties.getAtlassianApiToken()).isEqualTo("api-token");
                });
    }

    @Test
    void registersRestClientsWhenRequiredPropertiesExist() {
        contextRunner
                .withPropertyValues(
                        "external-context.github-token=github-token",
                        "external-context.atlassian-base-url=https://stolenmoments.atlassian.net",
                        "external-context.atlassian-email=batchbridge@example.com",
                        "external-context.atlassian-api-token=api-token"
                )
                .run(context -> {
                    assertThat(context).hasBean("githubRestClient");
                    assertThat(context).hasBean("atlassianRestClient");
                    assertThat(context.getBean("githubRestClient")).isInstanceOf(RestClient.class);
                    assertThat(context.getBean("atlassianRestClient")).isInstanceOf(RestClient.class);

                    String encodedCredentials = Base64.getEncoder()
                            .encodeToString("batchbridge@example.com:api-token".getBytes(StandardCharsets.UTF_8));
                    assertThat(encodedCredentials).isEqualTo("YmF0Y2hicmlkZ2VAZXhhbXBsZS5jb206YXBpLXRva2Vu");
                });
    }

    @Test
    void skipsRestClientsWhenPropertiesAreMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("githubRestClient");
            assertThat(context).doesNotHaveBean("atlassianRestClient");
        });
    }
}
