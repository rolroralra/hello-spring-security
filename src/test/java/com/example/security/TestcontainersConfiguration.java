package com.example.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 PostgreSQL 컨테이너. compose.yaml 과 같은 이미지를 사용한다.
 * @ServiceConnection 이 컨테이너 접속 정보를 DataSource 에 자동으로 연결해 준다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String POSTGRES_IMAGE = "postgres:18.6-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
    }
}
