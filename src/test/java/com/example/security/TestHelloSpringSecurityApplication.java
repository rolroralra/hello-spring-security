package com.example.security;

import org.springframework.boot.SpringApplication;

/**
 * docker compose 대신 Testcontainers 로 DB 를 띄워 애플리케이션을 실행한다.
 * ./gradlew bootTestRun
 */
public class TestHelloSpringSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.from(HelloSpringSecurityApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
