package com.example.security.auth;

import com.example.security.member.Member;
import com.example.security.member.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 인증된 사용자를 표현하는 객체 (Authentication#getPrincipal()).
 * 엔티티를 그대로 쓰지 않고 필요한 값만 담은 불변 객체로 만든다.
 */
public record MemberPrincipal(
        Long id,
        String username,
        String password,
        String nickname,
        Role role
) implements UserDetails {

    public static MemberPrincipal from(Member member) {
        return new MemberPrincipal(
                member.getId(),
                member.getUsername(),
                member.getPassword(),
                member.getNickname(),
                member.getRole()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        // 비밀번호 해시가 로그에 찍히지 않도록 한다
        return "MemberPrincipal[id=%d, username=%s, role=%s]".formatted(id, username, role);
    }
}
