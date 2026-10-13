package com.example.security.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Size(min = 4, max = 50)
        @Pattern(regexp = "^[a-z0-9_]+$", message = "영문 소문자, 숫자, _ 만 사용할 수 있습니다.")
        String username,

        @NotBlank
        @Size(min = 8, max = 64)
        @MaxBytes(BCRYPT_MAX_BYTES)
        String password,

        @NotBlank
        @Size(max = 50)
        String nickname
) {

    /** BCrypt 가 처리할 수 있는 최대 입력 길이 (docs/05 참고) */
    public static final int BCRYPT_MAX_BYTES = 72;

    public static SignupRequest empty() {
        return new SignupRequest("", "", "");
    }

    @Override
    public String toString() {
        // 로그에 비밀번호가 남지 않도록 한다
        return "SignupRequest[username=%s, nickname=%s]".formatted(username, nickname);
    }
}
