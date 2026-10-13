package com.example.security.member;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 일반 회원 가입. 비밀번호는 반드시 인코딩해서 저장한다. */
    @Transactional
    public MemberResponse signup(SignupRequest request) {
        return MemberResponse.from(register(request.username(), request.password(), request.nickname(), Role.USER));
    }

    @Transactional
    public Member register(String username, String rawPassword, String nickname, Role role) {
        if (memberRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException(username);
        }
        Member member = Member.create(username, passwordEncoder.encode(rawPassword), nickname, role);
        return memberRepository.save(member);
    }

    /** 본인 또는 관리자만 조회할 수 있다 (메서드 보안, docs/07 참고). */
    @PreAuthorize("#username == authentication.name or hasRole('ADMIN')")
    public MemberResponse findByUsername(String username) {
        return memberRepository.findByUsername(username)
                .map(MemberResponse::from)
                .orElseThrow(() -> new MemberNotFoundException(username));
    }

    /** 관리자만 전체 회원 목록을 볼 수 있다. */
    @PreAuthorize("hasRole('ADMIN')")
    public List<MemberResponse> findAll() {
        return memberRepository.findAll().stream()
                .map(MemberResponse::from)
                .toList();
    }
}
