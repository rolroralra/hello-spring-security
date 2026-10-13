package com.example.security.member;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.admin")
public record AdminProperties(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String nickname
) {

    @Override
    public String toString() {
        return "AdminProperties[username=%s, nickname=%s]".formatted(username, nickname);
    }
}
