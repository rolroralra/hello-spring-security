package com.example.security.member;

public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(String username) {
        super("회원을 찾을 수 없습니다: " + username);
    }
}
