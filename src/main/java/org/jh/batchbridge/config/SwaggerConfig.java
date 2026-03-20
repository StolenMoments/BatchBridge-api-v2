package org.jh.batchbridge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Value("${batch-bridge.swagger.contact.name}")
    private String contactName;

    @Value("${batch-bridge.swagger.contact.email}")
    private String contactEmail;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Batch Bridge API")
                        .description("Batch Bridge REST API documentation")
                        .version("v1")
                        .contact(new Contact()
                                .name(contactName)
                                .email(contactEmail))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(new Server().url("/")));
    }
}
