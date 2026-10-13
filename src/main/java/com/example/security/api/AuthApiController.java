package com.example.security.api;

import com.example.security.jwt.TokenService;
import com.example.security.jwt.TokenService.IssuedToken;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthApiController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    /** username/password 를 확인한 뒤 JWT 를 발급한다. 실패 시 BadCredentialsException → 401. */
    @PostMapping("/token")
    public ApiResponse<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );
        IssuedToken token = tokenService.issue(authentication);
        return ApiResponse.ok(new TokenResponse(token.value(), "Bearer", token.expiresAt()));
    }

    public record TokenRequest(@NotBlank String username, @NotBlank String password) {

        @Override
        public String toString() {
            return "TokenRequest[username=%s]".formatted(username);
        }
    }

    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {
    }
}
