package com.example.security.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** docs/04 - PasswordEncoder 의 동작을 눈으로 확인하는 학습 테스트. */
class PasswordEncoderTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void 인코딩_결과에는_알고리즘_id_접두사가_붙는다() {
        String encoded = encoder.encode("password1234");

        assertThat(encoded).startsWith("{bcrypt}$2a$");
    }

    @Test
    void 같은_비밀번호라도_salt_때문에_매번_다른_값이_나온다() {
        String first = encoder.encode("password1234");
        String second = encoder.encode("password1234");

        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("password1234", first)).isTrue();
        assertThat(encoder.matches("password1234", second)).isTrue();
    }

    @Test
    void 틀린_비밀번호는_매칭되지_않는다() {
        String encoded = encoder.encode("password1234");

        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void BCrypt_는_72바이트를_넘는_비밀번호를_인코딩하지_못한다() {
        String koreanPassword = "가".repeat(30); // 한글은 UTF-8 로 한 글자에 3바이트 → 90바이트

        assertThatThrownBy(() -> encoder.encode(koreanPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes");
    }

    @Test
    void 접두사로_알고리즘을_선택하므로_다른_인코더로_만든_해시도_검증할_수_있다() {
        String legacyBcrypt = "{bcrypt}" + new BCryptPasswordEncoder(4).encode("password1234");

        assertThat(encoder.matches("password1234", legacyBcrypt)).isTrue();
        assertThat(encoder.upgradeEncoding(legacyBcrypt)).isTrue();
    }
}
