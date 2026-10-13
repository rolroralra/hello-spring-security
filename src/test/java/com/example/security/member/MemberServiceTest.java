package com.example.security.member;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    @Test
    void 회원가입시_비밀번호를_인코딩해서_USER_권한으로_저장한다() {
        given(memberRepository.existsByUsername("alice")).willReturn(false);
        given(passwordEncoder.encode("password1234")).willReturn("{bcrypt}encoded");
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        MemberResponse response = memberService.signup(new SignupRequest("alice", "password1234", "앨리스"));

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("{bcrypt}encoded");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(response.username()).isEqualTo("alice");
    }

    @Test
    void 이미_존재하는_아이디면_예외가_발생한다() {
        given(memberRepository.existsByUsername("alice")).willReturn(true);

        assertThatThrownBy(() -> memberService.signup(new SignupRequest("alice", "password1234", "앨리스")))
                .isInstanceOf(DuplicateUsernameException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_회원_조회시_예외가_발생한다() {
        given(memberRepository.findByUsername("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.findByUsername("nobody"))
                .isInstanceOf(MemberNotFoundException.class);
    }
}
