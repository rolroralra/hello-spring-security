package com.example.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HelloSpringSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloSpringSecurityApplication.class, args);
    }
}
