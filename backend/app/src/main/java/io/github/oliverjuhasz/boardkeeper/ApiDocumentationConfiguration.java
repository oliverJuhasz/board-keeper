package io.github.oliverjuhasz.boardkeeper;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApiDocumentationConfiguration {

    private static final String API_KEY = "apiKey";

    /** The header of the OpenAPI description that springdoc builds from the controllers. */
    @Bean
    OpenAPI boardKeeperApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Board Keeper API")
                        .version("1")
                        .license(new License().name("AGPL-3.0-or-later").identifier("AGPL-3.0-or-later")))
                // A relative address, so the description is the same wherever the app is installed.
                .servers(List.of(new Server().url("/")))
                // How a program proves who it is: with an API Key (ADR 0017). Declaring it gives Swagger UI
                // an "Authorize" button, and the key pasted there goes along with every "Try it out".
                // The frontend signs in with a session cookie instead, which needs no declaring here.
                .addSecurityItem(new SecurityRequirement().addList(API_KEY))
                // What passkeys exchange with the browser. Its shape is WebAuthn's, not ours, so the
                // description leaves it open (see PasskeyController).
                .components(new Components()
                        .addSecuritySchemes(
                                API_KEY,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .description("An API Key, created on the settings page. It starts with bk_."))
                        .addSchemas(
                                "WebAuthnJson",
                                new ObjectSchema()
                                        .additionalProperties(true)
                                        .description("WebAuthn options or a credential, in the JSON form of WebAuthn Level 3")));
    }
}
