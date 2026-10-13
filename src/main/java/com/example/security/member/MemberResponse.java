package com.example.security.member;

import java.time.Instant;

/** 외부로 노출하는 회원 정보. 비밀번호는 절대 포함하지 않는다. */
public record MemberResponse(Long id, String username, String nickname, Role role, Instant createdAt) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getUsername(),
                member.getNickname(),
                member.getRole(),
                member.getCreatedAt()
        );
    }
}
