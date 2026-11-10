package io.github.oliverjuhasz.boardkeeper;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A throwaway PostgreSQL in Docker for tests and for local development.
 * {@code @ServiceConnection} makes Spring Boot connect to it, with no datasource settings needed.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(System.getProperty("boardkeeper.postgres.image", "postgres:18-alpine"));
    }
}
