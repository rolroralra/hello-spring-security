# 07. 인가 - URL 과 메서드 보안

## 학습 목표

- Role 과 Authority 의 관계를 설명할 수 있다.
- `authorizeHttpRequests` 로 URL 별 접근 규칙을 작성하고, 규칙의 **순서**가 왜 중요한지 안다.
- `@PreAuthorize` 로 메서드 단위의 세밀한 권한 검사를 할 수 있다.

## 1. Role vs Authority

Spring Security 에서 권한은 결국 **문자열**(`GrantedAuthority`)이다. Role 은 그중 `ROLE_` 로 시작하는 것에 붙인 별명이다.

| 표현 | 실제로 검사하는 문자열 |
|------|------------------------|
| `hasRole("ADMIN")` | `ROLE_ADMIN` |
| `hasAuthority("ROLE_ADMIN")` | `ROLE_ADMIN` |
| `hasAuthority("member:delete")` | `member:delete` |

- **Role** 은 "관리자", "일반 회원" 같은 굵직한 역할 분류에 쓴다.
- **Authority** 는 "회원 삭제 가능" 같은 세밀한 권한에 쓴다.

> 흔한 실수: `hasRole("ROLE_ADMIN")` 이라고 쓰면 안 된다. 접두사가 자동으로 붙기 때문에 Spring Security 가 예외를 던진다.
> 반대로 사용자에게 권한을 줄 때는 `ROLE_` 를 붙여야 한다. 우리 프로젝트는 `Role#authority()` 가 이를 담당한다.

## 2. URL 기반 인가 - authorizeHttpRequests

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
        .requestMatchers("/", "/login", "/signup", "/error", "/access-denied").permitAll()
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated()
)
```

### 규칙은 위에서부터 순서대로, 처음 매칭되는 것 하나만 적용된다

```java
// 잘못된 예
.anyRequest().authenticated()
.requestMatchers("/admin/**").hasRole("ADMIN")   // 도달하지 않는다 → 애플리케이션 기동 시 에러
```

**구체적인 규칙을 위에, 넓은 규칙을 아래에** 둔다. 그리고 마지막에는 항상 `anyRequest()` 를 둬서 빠진 URL 이 없게 한다.
새 URL 을 추가했는데 규칙을 깜빡해도 `anyRequest().authenticated()` 덕분에 기본적으로 막힌다(secure by default).

### 자주 쓰는 규칙

| 메서드 | 의미 |
|--------|------|
| `permitAll()` | 누구나 (로그인 불필요) |
| `authenticated()` | 로그인한 사용자 |
| `hasRole("ADMIN")` | ROLE_ADMIN 보유 |
| `hasAnyRole("ADMIN", "MANAGER")` | 둘 중 하나 보유 |
| `hasAuthority("member:delete")` | 특정 Authority 보유 |
| `denyAll()` | 아무도 안 됨 |

### HTTP 메서드별 규칙

[`ApiSecurityConfig.java`](../src/main/java/com/example/security/config/ApiSecurityConfig.java)

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.POST, "/api/auth/token", "/api/members").permitAll()  // POST 만 허용
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated()
)
```

`POST /api/members`(회원가입)는 누구나 가능하지만, `GET /api/members/{username}` 은 `anyRequest().authenticated()` 에 걸려 로그인이 필요하다.

### URL 패턴 문법

`requestMatchers("/admin/**")` 의 패턴은 Spring MVC 의 `@GetMapping` 과 같은 **PathPattern** 문법을 쓴다.

| 패턴 | 매칭 예 |
|------|---------|
| `/admin/*` | `/admin/members` (한 단계만) |
| `/admin/**` | `/admin/members`, `/admin/members/1` (모든 하위 경로) |
| `/members/{id}` | `/members/1` |

## 3. 메서드 보안 - @PreAuthorize

URL 규칙만으로는 표현하기 어려운 경우가 있다.

> "회원 정보 조회(`GET /api/members/{username}`)는 **본인 것만**, 단 ADMIN 은 누구 것이든"

URL 만 보고는 "본인" 인지 알 수 없다. 이럴 때 메서드 보안을 쓴다.

### 활성화

```java
@Configuration
@EnableMethodSecurity      // @PreAuthorize, @PostAuthorize 등 활성화
public class SecurityConfig { ... }
```

### 사용

[`MemberService.java`](../src/main/java/com/example/security/member/MemberService.java)

```java
/** 본인 또는 관리자만 조회할 수 있다. */
@PreAuthorize("#username == authentication.name or hasRole('ADMIN')")
public MemberResponse findByUsername(String username) { ... }

/** 관리자만 전체 회원 목록을 볼 수 있다. */
@PreAuthorize("hasRole('ADMIN')")
public List<MemberResponse> findAll() { ... }
```

- `#username` : 메서드 파라미터 `username` 의 값
- `authentication` : 현재 `Authentication` 객체. `authentication.name` 은 로그인한 사용자의 username
- 메서드가 **실행되기 전에** 표현식을 평가하고, 거짓이면 `AccessDeniedException` → 403

> `#username` 처럼 파라미터 이름으로 참조하려면 컴파일 시 `-parameters` 옵션이 필요하다.
> Spring Boot Gradle 플러그인이 자동으로 켜 주므로 별도 설정은 필요 없다.

`@PreAuthorize` 는 Spring AOP 프록시로 동작한다. 그래서 **같은 클래스 안에서 자기 메서드를 호출하면 검사가 적용되지 않는다** (`this.findAll()`). JPA 의 `@Transactional` 과 같은 제약이다.

### 그 밖의 애너테이션

| 애너테이션 | 용도 |
|-----------|------|
| `@PreAuthorize` | 실행 전 검사 (가장 많이 씀) |
| `@PostAuthorize("returnObject.username == authentication.name")` | 실행 후 반환값으로 검사 |
| `@PreFilter` / `@PostFilter` | 컬렉션 파라미터/반환값을 권한에 따라 걸러냄 |

## 4. 다층 방어 (Defense in Depth)

우리 프로젝트에서 관리자 회원 목록은 **두 겹**으로 보호된다.

```
GET /admin/members
  ① URL 규칙       .requestMatchers("/admin/**").hasRole("ADMIN")      ← SecurityConfig
  ② 메서드 보안     @PreAuthorize("hasRole('ADMIN')") findAll()          ← MemberService

GET /api/admin/members
  ① URL 규칙       .requestMatchers("/api/admin/**").hasRole("ADMIN")  ← ApiSecurityConfig
  ② 메서드 보안     @PreAuthorize("hasRole('ADMIN')") findAll()          ← MemberService
```

누군가 실수로 URL 규칙을 지우거나, 다른 컨트롤러에서 `findAll()` 을 호출해도 서비스 계층에서 한 번 더 막힌다.
**URL 규칙은 입구, 메서드 보안은 금고**라고 생각하자.

## 5. 권한이 없을 때의 응답

03장에서 본 것처럼 `ExceptionTranslationFilter` 가 상황에 따라 응답을 정한다.

| 상황 | 웹 체인 | API 체인 |
|------|---------|----------|
| 로그인 안 함 | 302 → `/login` | 401 |
| 로그인 했지만 권한 없음 | 403 → `/access-denied` 화면 | 403 |

웹 체인의 403 화면은 다음 설정으로 지정했다.

```java
.exceptionHandling(ex -> ex.accessDeniedPage("/access-denied"))
```

> **실제로 겪은 버그**: `accessDeniedPage` 는 리다이렉트가 아니라 **forward** 다. 원래 요청의 HTTP 메서드가 그대로 유지된다.
> 처음에 403 화면 컨트롤러를 `@GetMapping("/access-denied")` 로 만들었더니, CSRF 토큰 없는 **POST** 가 거부될 때
> forward 된 `POST /access-denied` 를 처리할 핸들러가 없어서 403 대신 **405 Method Not Allowed** 가 응답되었다.
> 그래서 [`HomeController`](../src/main/java/com/example/security/web/HomeController.java) 는 `@RequestMapping("/access-denied")` 로 모든 메서드를 받는다.
> MockMvc 테스트는 forward 를 실제로 실행하지 않아 이 버그를 잡지 못했고, curl 로 실제 서버를 호출해 보고서야 발견했다 (10장).

> `@PreAuthorize` 가 던지는 `AccessDeniedException` 을 `@RestControllerAdvice` 에서 `Exception.class` 로 잡아 버리면
> Spring Security 가 처리하지 못해 500 이 되거나 엉뚱한 응답이 나간다.
> 그래서 [`ApiExceptionHandler`](../src/main/java/com/example/security/api/ApiExceptionHandler.java) 는 필요한 예외만 골라서 처리한다.

## 직접 해보기

1. `alice`(USER) 로 로그인해서 http://localhost:8080/admin/members 에 접근해 보자. 어떤 화면이 나오는가?
2. 로그아웃 상태에서 같은 URL 에 접근하면? 1번과 무엇이 다른가?
3. `admin` 으로 로그인해서 상단 메뉴에 "회원 관리" 링크가 보이는지, `alice` 로는 안 보이는지 확인해 보자.
4. (도전) `SecurityConfig` 에서 `.requestMatchers("/admin/**").hasRole("ADMIN")` 줄을 지우고 `alice` 로 `/admin/members` 에 접근해 보자.
   URL 규칙이 없어도 막히는가? 어느 계층에서 막혔는가? (확인 후 원복)

<details>
<summary>4번 해설</summary>

URL 규칙이 없어져서 `/admin/members` 는 `anyRequest().authenticated()` 에 해당되므로 로그인한 `alice` 는 컨트롤러까지 도달한다.
하지만 컨트롤러가 호출하는 `MemberService#findAll()` 의 `@PreAuthorize("hasRole('ADMIN')")` 에서 `AccessDeniedException` 이 발생해 403 화면이 나온다.
다층 방어가 동작한 것이다.

</details>

## 정리

- Role 은 `ROLE_` 접두사가 붙은 Authority 다. `hasRole("ADMIN")` == `hasAuthority("ROLE_ADMIN")`.
- `authorizeHttpRequests` 규칙은 위에서부터 처음 매칭되는 하나가 적용된다. 구체적인 규칙을 위에, `anyRequest()` 를 마지막에.
- 리소스 소유자 확인처럼 URL 로 표현하기 어려운 규칙은 `@PreAuthorize` 로 작성한다.
- URL 규칙과 메서드 보안을 함께 쓰면 실수에 강한 다층 방어가 된다.

---

| 이전 | 목차 | 다음 |
|:-----|:----:|-----:|
| [← 06. 폼 로그인과 로그아웃](06-form-login.md) | [목차](../README.md) | [08. CSRF →](08-csrf.md) |
