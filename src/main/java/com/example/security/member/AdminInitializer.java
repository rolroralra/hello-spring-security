package com.example.security.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 기동 시 관리자 계정이 없으면 하나 만들어 둔다. */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final AdminProperties adminProperties;

    public AdminInitializer(MemberRepository memberRepository, MemberService memberService,
                            AdminProperties adminProperties) {
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.adminProperties = adminProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (memberRepository.existsByUsername(adminProperties.username())) {
            return;
        }
        memberService.register(adminProperties.username(), adminProperties.password(),
                adminProperties.nickname(), Role.ADMIN);
        log.info("관리자 계정을 생성했습니다: {}", adminProperties.username());
    }
}
