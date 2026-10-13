package com.example.security.api;

import com.example.security.member.MemberResponse;
import com.example.security.member.MemberService;
import com.example.security.member.SignupRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MemberApiController {

    private final MemberService memberService;

    public MemberApiController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(memberService.signup(request));
    }

    /** JWT 인증에서는 principal 이 Jwt 객체다. sub 클레임 = username. */
    @GetMapping("/me")
    public ApiResponse<MemberResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(memberService.findByUsername(jwt.getSubject()));
    }

    /** 본인 또는 ADMIN 만 허용 — MemberService#findByUsername 의 @PreAuthorize 가 검사한다. */
    @GetMapping("/members/{username}")
    public ApiResponse<MemberResponse> member(@PathVariable String username) {
        return ApiResponse.ok(memberService.findByUsername(username));
    }

    @GetMapping("/admin/members")
    public ApiResponse<List<MemberResponse>> members() {
        return ApiResponse.ok(memberService.findAll());
    }
}
