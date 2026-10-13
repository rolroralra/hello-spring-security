package com.example.security.member;

/**
 * 회원 권한.
 * Spring Security 에서 "역할(Role)" 은 "ROLE_" 접두사가 붙은 권한(Authority) 문자열이다.
 */
public enum Role {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
