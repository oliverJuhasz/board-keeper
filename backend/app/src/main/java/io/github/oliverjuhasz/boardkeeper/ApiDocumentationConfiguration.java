package io.github.oliverjuhasz.boardkeeper;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApiDocumentationConfiguration {

    /** The header of the OpenAPI description that springdoc builds from the controllers. */
    @Bean
    OpenAPI boardKeeperApi() {
        return new OpenAPI()
                .info(new Info().title("Board Keeper API").version("1"))
                // A relative address, so the description is the same wherever the app is installed.
                .servers(List.of(new Server().url("/")))
                // What passkeys exchange with the browser. Its shape is WebAuthn's, not ours, so the
                // description leaves it open (see PasskeyController).
                .components(new Components()
                        .addSchemas(
                                "WebAuthnJson",
                                new ObjectSchema()
                                        .additionalProperties(true)
                                        .description("WebAuthn options or a credential, in the JSON form of WebAuthn Level 3")));
    }
}
