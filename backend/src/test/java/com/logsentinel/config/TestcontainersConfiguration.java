package com.logsentinel.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared Testcontainers configuration for integration tests.
 * Uses pgvector/pgvector:pg16 image to support the vector extension
 * required by V1__init_schema.sql.
 *
 * NOTE: Uses org.testcontainers.postgresql.PostgreSQLContainer (Testcontainers 2.x).
 * The old org.testcontainers.containers.PostgreSQLContainer is deprecated.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("pgvector/pgvector:pg16")
                .withDatabaseName("logsentinel_test")
                .withUsername("test")
                .withPassword("test");
    }
}
