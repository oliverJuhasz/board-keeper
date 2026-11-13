package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;

/**
 * Spring Security's parts for passkeys. It keeps passkeys in two tables of its own (see
 * V13__passkeys.sql), which it reads and writes with plain JDBC, not with jOOQ.
 */
@Configuration(proxyBeanMethods = false)
class PasskeyConfiguration {

    /** The WebAuthn "user" of each Account that has a passkey. */
    @Bean
    JdbcPublicKeyCredentialUserEntityRepository passkeyUsers(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    /** The passkeys themselves: one public key each. */
    @Bean
    JdbcUserCredentialRepository passkeyCredentials(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    /**
     * Teaches the API's JSON mapper the WebAuthn types, in the JSON form browsers use: binary
     * values as base64url text, for example. Spring Boot adds every JacksonModule bean to the
     * mapper.
     */
    @Bean
    WebauthnJacksonModule webauthnJacksonModule() {
        return new WebauthnJacksonModule();
    }
}
