package com.planwise;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A real PostgreSQL in Docker for tests. Import it with
 * {@code @Import(TestcontainersConfiguration.class)}; Spring starts the container once and
 * points spring.datasource.* at it via {@code @ServiceConnection}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17");
    }
}
