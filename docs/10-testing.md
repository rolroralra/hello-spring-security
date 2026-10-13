# 10. 보안 테스트

## 학습 목표

- `spring-security-test` 가 제공하는 애너테이션과 MockMvc 확장 기능을 사용할 수 있다.
- `@WithMockUser` 와 `@WithUserDetails` 의 차이를 안다.
- Testcontainers 로 실제 PostgreSQL 에 대한 통합 테스트를 작성한다.

## 1. 왜 보안 테스트가 중요한가

보안 설정은 **"되어야 하는 것" 만큼 "안 되어야 하는 것" 이 중요하다.**
기능 테스트는 보통 성공 경로만 확인하지만, 보안 테스트는 "USER 는 관리자 페이지에 **못 들어가야 한다**" 같은 실패 경로를 반드시 확인해야 한다.
설정 한 줄을 실수로 지웠을 때 바로 알려주는 안전망이 된다.

## 2. 테스트 구성

| 테스트 | 종류 | DB | 확인하는 것 |
|--------|------|:--:|-------------|
| [`PasswordEncoderTest`](../src/test/java/com/example/security/auth/PasswordEncoderTest.java) | 단위 (학습 테스트) | X | BCrypt, Delegating, 72바이트 제한 (04장) |
| [`MemberServiceTest`](../src/test/java/com/example/security/member/MemberServiceTest.java) | 단위 (Mockito) | X | 가입 시 인코딩, 중복 아이디 (05장) |
| [`MemberRepositoryTest`](../src/test/java/com/example/security/member/MemberRepositoryTest.java) | `@DataJpaTest` | O | 쿼리 메서드, 유니크 제약 (05장) |
| [`WebSecurityTest`](../src/test/java/com/example/security/web/WebSecurityTest.java) | `@SpringBootTest` + MockMvc | O | 폼 로그인, 인가, CSRF, 로그아웃 (06~08장) |
| [`ApiSecurityTest`](../src/test/java/com/example/security/api/ApiSecurityTest.java) | `@SpringBootTest` + MockMvc | O | 토큰 발급, JWT 인증, `@PreAuthorize` (07, 09장) |

```bash
./gradlew test                                   # 전체 (Docker 필요)
./gradlew test --tests '*WebSecurityTest'        # 특정 클래스만
open build/reports/tests/test/index.html         # 결과 리포트
```

## 3. Testcontainers - 진짜 PostgreSQL 로 테스트

[`TestcontainersConfiguration.java`](../src/test/java/com/example/security/TestcontainersConfiguration.java)

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String POSTGRES_IMAGE = "postgres:18.6-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
    }
}
```

- 테스트 시작 시 `compose.yaml` 과 **같은 버전**의 PostgreSQL 컨테이너를 띄운다. H2 같은 대체 DB 를 쓰면 SQL 방언 차이로 운영에서만 나는 버그를 놓칠 수 있다.
- `@ServiceConnection` 이 컨테이너의 접속 정보를 DataSource 에 자동 연결한다. URL, 포트를 신경 쓸 필요가 없다.
- 테스트 클래스에서 `@Import(TestcontainersConfiguration.class)` 로 가져다 쓴다.
- 같은 설정을 쓰는 테스트들은 Spring 컨텍스트(와 컨테이너)를 **재사용**하므로 생각보다 빠르다.

## 4. 인증된 사용자 흉내 내기

### @WithMockUser - 가짜 사용자

```java
@Test
@WithMockUser(roles = "USER")
void USER_는_관리자_페이지에_접근할_수_없다() throws Exception {
    mockMvc.perform(get("/admin/members"))
            .andExpect(status().isForbidden());
}
```

DB 를 거치지 않고 `SecurityContext` 에 가짜 `Authentication` 을 넣는다. 기본값은 username `user`, 비밀번호 `password`, 역할 `USER`.
**권한 규칙만** 확인할 때 빠르고 편하다.

### @WithUserDetails - 실제 UserDetailsService 로 조회한 사용자

```java
@Test
@WithUserDetails("admin")
void 내_정보_페이지에서_현재_사용자_정보를_보여준다() throws Exception {
    mockMvc.perform(get("/me"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("관리자")));
}
```

**`/me` 테스트에 `@WithMockUser` 를 쓰면 실패한다.** 왜일까?

- `@WithMockUser` 의 principal 은 Spring 의 `User` 객체다.
- `HomeController#me` 는 `@AuthenticationPrincipal MemberPrincipal` 을 받는다. 타입이 맞지 않으니 `null` 이 주입되고, 템플릿에서 `${me.id}` 를 그리다 에러가 난다.

`@WithUserDetails("admin")` 은 **우리의 `MemberUserDetailsService` 로 실제 DB 에서 조회**한 `MemberPrincipal` 을 principal 로 사용한다.
principal 의 타입이나 값이 중요한 테스트에서는 `@WithUserDetails` 를 쓰자.

| | `@WithMockUser` | `@WithUserDetails` |
|---|---|---|
| principal | Spring `User` (가짜) | `UserDetailsService` 가 반환한 객체 |
| DB 필요 | X | O (사용자가 존재해야 함) |
| 적합한 상황 | URL/메서드 권한 규칙 확인 | principal 의 실제 값을 쓰는 코드 |

## 5. MockMvc 보안 확장

`SecurityMockMvcRequestBuilders`, `SecurityMockMvcRequestPostProcessors`, `SecurityMockMvcResultMatchers` 를 static import 해서 쓴다.

### 폼 로그인 - formLogin()

```java
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
```

`formLogin()` 은 CSRF 토큰까지 포함한 진짜 `POST /login` 요청을 만든다. **실제 DB 의 계정으로 실제 인증 흐름 전체**를 검증한다.

### CSRF - csrf()

```java
@Test
void CSRF_토큰_없이_POST_하면_거부된다() throws Exception {
    mockMvc.perform(post("/signup")
                    .param("username", "no_csrf_user")
                    .param("password", "password1234")
                    .param("nickname", "토큰없음"))
            .andExpect(status().isForbidden());
}

@Test
void 회원가입에_성공하면_로그인_페이지로_이동하고_새_계정으로_로그인할_수_있다() throws Exception {
    mockMvc.perform(post("/signup").with(csrf())          // 유효한 CSRF 토큰 추가
                    .param("username", "web_user")
                    ...)
            .andExpect(redirectedUrl("/login?signup"));
}
```

`.with(csrf())` 를 빼먹으면 POST 테스트가 이유 없이 403 으로 실패한다. 처음 보안 테스트를 작성할 때 가장 흔히 만나는 실수다.

### JWT - jwt()

```java
@Test
void USER_권한으로_관리자_API_를_호출하면_403() throws Exception {
    mockMvc.perform(get("/api/admin/members")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
}

@Test
void 다른_사람의_회원정보는_조회할_수_없다_PreAuthorize() throws Exception {
    mockMvc.perform(get("/api/members/admin")
                    .with(jwt().jwt(token -> token.subject("someone"))
                            .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
}
```

`jwt()` 는 실제 토큰을 만들지 않고 **이미 검증된 JWT 인증 상태**를 만들어 준다. 서명 키가 필요 없어서 권한 규칙 테스트에 편하다.
`jwt().jwt(token -> token.subject(...))` 로 클레임을, `.authorities(...)` 로 권한을 지정한다.

반대로 **토큰 발급부터 검증까지 전체 흐름**은 진짜 토큰으로 확인한다.

```java
@Test
void 토큰을_발급받아_내_정보를_조회한다() throws Exception {
    String accessToken = issueToken("admin", "admin1234");   // POST /api/auth/token

    mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.password").doesNotExist());   // 비밀번호 노출 여부도 확인
}
```

### 정리표

| 도구 | 용도 |
|------|------|
| `@WithMockUser` | 가짜 사용자로 권한 규칙 테스트 |
| `@WithUserDetails("name")` | 실제 UserDetailsService 로 조회한 사용자 |
| `formLogin()` / `logout()` | 폼 로그인 / 로그아웃 요청 |
| `.with(csrf())` | CSRF 토큰 추가 |
| `.with(user("name").roles(...))` | 요청 단위로 사용자 지정 |
| `.with(jwt())` | JWT 인증 상태 흉내 |
| `authenticated()` / `unauthenticated()` | 결과의 인증 상태 확인 |

## 6. 테스트를 작성하며 발견한 것들

이 프로젝트의 테스트는 Spring Security 7 의 실제 동작을 확인하는 과정에서 몇 가지를 알려 주었다. 버전이 바뀌면 테스트가 먼저 알려준다는 좋은 예다.

| 발견 | 내용 | 관련 장 |
|------|------|---------|
| 로그인 리다이렉트가 상대 경로 | 미인증 접근 시 `Location` 이 `http://localhost/login` 이 아니라 `/login` | 06 |
| `WWW-Authenticate` 헤더 변화 | `Bearer` 뒤에 `resource_metadata="..."` 가 붙는다 (RFC 9728) | 09 |
| `FACTOR_PASSWORD` 권한 | JWT `roles` 에 섞여 들어가 필터링 로직 추가 | 06, 09 |
| BCrypt 72바이트 제한 | 긴 한글 비밀번호로 가입 시 500 → `@MaxBytes` 검증 추가 | 05 |
| 403 대신 405 | CSRF 없는 POST 가 `/access-denied` 로 forward 되는데 GET 만 받아서 405 → `@RequestMapping` 으로 수정 | 07 |

마지막 항목은 **MockMvc 테스트가 통과했는데도** 실제 서버에서는 버그였다. MockMvc 는 forward 를 기록만 하고(`forwardedUrl`) 실제로 실행하지 않기 때문이다.
그래서 `WebSecurityTest` 에 forward 대상(`POST /access-denied`)을 직접 호출하는 테스트를 추가했다.
**MockMvc 테스트와 함께, 실제 서버를 띄워 curl/브라우저로 확인하는 과정도 꼭 거치자.**

## 직접 해보기

1. `WebSecurityTest` 에 "로그인하지 않은 사용자가 `/admin/members` 에 접근하면 `/login` 으로 리다이렉트된다" 테스트를 추가해 보자.
2. `ApiSecurityTest` 에 "`/api/members/{username}` 을 ADMIN 은 누구 것이든 조회할 수 있다" 테스트를 `jwt()` 로 추가해 보자.
3. `SecurityConfig` 에서 `.requestMatchers("/admin/**").hasRole("ADMIN")` 을 `.authenticated()` 로 바꾸고 테스트를 돌려 보자. 어떤 테스트가 실패하는가? (확인 후 원복)

## 마무리 - 지금까지 배운 것

```
요청 ─▶ DelegatingFilterProxy ─▶ FilterChainProxy ─┬─▶ API 체인 (/api/**, JWT, Stateless, CSRF off)
                                                   └─▶ 웹 체인  (폼 로그인, 세션, CSRF on)
             인증: AuthenticationManager ─▶ DaoAuthenticationProvider ─▶ MemberUserDetailsService(JPA) + PasswordEncoder(BCrypt)
             인가: authorizeHttpRequests (URL) + @PreAuthorize (메서드)
```

### 더 공부할 주제

| 주제 | 키워드 |
|------|--------|
| 소셜 로그인 | `oauth2Login()`, Google/GitHub/Kakao 로그인 |
| 인증 서버 구축 | Spring Authorization Server |
| 비밀번호 없는 로그인 | Passkeys(WebAuthn), One-Time Token 로그인 |
| 다중 인증 | Spring Security 7 의 MFA 지원, `FACTOR_*` 권한 |
| 세션 관리 | 동시 로그인 제한(`maximumSessions`), Spring Session + Redis |
| 로그인 유지 | Remember-Me |
| 공식 문서 | https://docs.spring.io/spring-security/reference/ |

수고하셨습니다!

---

| 이전 | 목차 | 다음 |
|:-----|:----:|-----:|
| [← 09. JWT 로 REST API 보호하기](09-jwt-rest-api.md) | [목차](../README.md) | |
