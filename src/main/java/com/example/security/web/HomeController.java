package com.example.security.web;

import com.example.security.auth.MemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    /** 로그인한 사용자만 접근 가능. @AuthenticationPrincipal 로 현재 사용자를 주입받는다 (docs/06 참고). */
    @GetMapping("/me")
    public String me(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("me", principal);
        return "me";
    }

    /**
     * 403 화면. AccessDeniedHandler 는 원래 요청(POST 등)을 그대로 이 경로로 forward 하므로
     * GET 만 받으면 POST 요청이 거부될 때 403 대신 405 가 응답된다. 그래서 모든 메서드를 받는다.
     */
    @RequestMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}
