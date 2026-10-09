package com.eljaibo.pos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:18-alpine")
                .withDatabaseName("jaibo_pos_test")
                .withUsername("test")
                .withPassword("test");
    }

}
