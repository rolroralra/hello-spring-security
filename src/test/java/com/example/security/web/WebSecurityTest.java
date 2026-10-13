package com.example.security.web;

import com.example.security.TestcontainersConfiguration;
import com.example.security.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** docs/10 - 폼 로그인(세션) 기반 보안 설정 통합 테스트. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WebSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 홈은_누구나_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    void 로그인하지_않고_보호된_페이지에_접근하면_로그인_페이지로_이동한다() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 올바른_아이디와_비밀번호로_로그인하면_인증된다() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("admin1234"))
                .andExpect(authenticated().withUsername("admin").withRoles("ADMIN"))
                .andExpect(redirectedUrl("/me"));
    }

    @Test
    void 비밀번호가_틀리면_인증되지_않는다() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @WithUserDetails("admin")
    void 내_정보_페이지에서_현재_사용자_정보를_보여준다() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("관리자")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void USER_는_관리자_페이지에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/admin/members"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("admin")
    void ADMIN_은_관리자_페이지에_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/admin/members"))
                .andExpect(status().isOk());
    }

    @Test
    void CSRF_토큰_없이_POST_하면_거부된다() throws Exception {
        mockMvc.perform(post("/signup")
                        .param("username", "no_csrf_user")
                        .param("password", "password1234")
                        .param("nickname", "토큰없음"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/access-denied"));

        assertThat(memberRepository.existsByUsername("no_csrf_user")).isFalse();
    }

    @Test
    void 접근_거부_페이지는_POST_forward_도_처리한다() throws Exception {
        // MockMvc 는 forward 를 실제로 실행하지 않으므로, forward 된 POST 를 직접 호출해 405 가 아님을 확인한다
        mockMvc.perform(post("/access-denied").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("접근 권한이 없습니다")));
    }

    @Test
    void 회원가입에_성공하면_로그인_페이지로_이동하고_새_계정으로_로그인할_수_있다() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("username", "web_user")
                        .param("password", "password1234")
                        .param("nickname", "웹유저"))
                .andExpect(redirectedUrl("/login?signup"));

        mockMvc.perform(formLogin("/login").user("web_user").password("password1234"))
                .andExpect(authenticated().withRoles("USER"));
    }

    @Test
    void 잘못된_입력으로_회원가입하면_폼을_다시_보여준다() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("username", "X")
                        .param("password", "short")
                        .param("nickname", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("회원가입")));
    }

    @Test
    @WithMockUser
    void 로그아웃하면_홈으로_이동한다() throws Exception {
        mockMvc.perform(logout())
                .andExpect(redirectedUrl("/?logout"))
                .andExpect(unauthenticated());
    }
}
