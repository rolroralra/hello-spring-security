# Hello Spring Security

Spring Security를 처음 배우는 개발자를 위한 **단계별 실습 강의**입니다.
"의존성 하나 추가했더니 갑자기 로그인 화면이 뜨네?"에서 시작해서
**JPA 기반 회원 인증 → 폼 로그인 → 권한 관리 → CSRF → JWT REST API → 보안 테스트**까지 차근차근 진행합니다.

모든 장(chapter)은 이 저장소의 **실제로 동작하는 코드**를 기준으로 설명합니다.
문서를 읽고 → 코드를 열어 보고 → 직접 실행해서 확인하는 흐름으로 학습하세요.

## 기술 스택

| 구분 | 버전 |
|------|------|
| Java | 25 (Gradle Toolchain 이 자동으로 내려받음) |
| Spring Boot | 4.1.1 |
| Spring Security | 7.1.1 |
| Spring Framework | 7.0.9 |
| Spring Data JPA / Hibernate | 4.1.1 / 7.4.5 |
| Build | Gradle 9.7.1 (Kotlin DSL) |
| Database | PostgreSQL 18.6 (`postgres:18.6-alpine`, Docker Compose) |
| View | Thymeleaf + thymeleaf-extras-springsecurity6 |
| Test | JUnit 5, spring-security-test, Testcontainers 2 |

## 빠른 시작

```bash
# 1. Docker Desktop 실행 후
./gradlew bootRun          # compose.yaml 의 PostgreSQL 컨테이너가 자동으로 함께 뜬다

# 2. 브라우저에서 접속
open http://localhost:8080 # 관리자 계정: admin / admin1234

# 3. 테스트 (Testcontainers 가 테스트용 PostgreSQL 을 띄운다)
./gradlew test
```

자세한 내용은 [00. 실습 환경 준비](docs/00-setup.md)를 참고하세요.

## 커리큘럼

| # | 문서 | 배우는 내용 | 관련 코드 |
|---|------|-------------|-----------|
| 00 | [실습 환경 준비](docs/00-setup.md) | JDK 25 Toolchain, Docker Compose PostgreSQL, 프로젝트 구조 | `build.gradle.kts`, `compose.yaml` |
| 01 | [Spring Security 핵심 개념](docs/01-concepts.md) | 인증 vs 인가, 401 vs 403, Principal / Authority / SecurityContext | - |
| 02 | [자동 구성 - 아무것도 안 했는데 로그인 화면이?](docs/02-auto-configuration.md) | Spring Boot 기본 보안 설정과 그 설정을 "교체" 하는 방법 | `SecurityConfig` |
| 03 | [아키텍처 - 필터 체인](docs/03-architecture.md) | DelegatingFilterProxy, FilterChainProxy, SecurityFilterChain, 인증 흐름 | `SecurityConfig`, `ApiSecurityConfig` |
| 04 | [PasswordEncoder](docs/04-password-encoder.md) | 해시, Salt, BCrypt, DelegatingPasswordEncoder | `PasswordEncoderTest` |
| 05 | [UserDetailsService 와 JPA 회원](docs/05-userdetails-jpa.md) | Member 엔티티, UserDetails, DaoAuthenticationProvider, 회원가입 | `member/*`, `auth/*` |
| 06 | [폼 로그인과 로그아웃](docs/06-form-login.md) | 커스텀 로그인 페이지, 세션, 현재 사용자 꺼내기 | `web/*`, `templates/*` |
| 07 | [인가 - URL 과 메서드 보안](docs/07-authorization.md) | Role vs Authority, `authorizeHttpRequests`, `@PreAuthorize` | `SecurityConfig`, `MemberService` |
| 08 | [CSRF](docs/08-csrf.md) | CSRF 공격 원리와 방어, API 에서 끄는 이유 | `SecurityConfig`, `ApiSecurityConfig` |
| 09 | [JWT 로 REST API 보호하기](docs/09-jwt-rest-api.md) | Stateless 인증, 토큰 발급/검증, 다중 SecurityFilterChain | `jwt/*`, `api/*` |
| 10 | [보안 테스트](docs/10-testing.md) | `@WithMockUser`, `@WithUserDetails`, `csrf()`, `jwt()`, Testcontainers | `src/test/**` |

## 완성된 애플리케이션 둘러보기

### 화면 (세션 + 폼 로그인)

| URL | 접근 권한 | 설명 |
|-----|-----------|------|
| `GET /` | 누구나 | 홈 |
| `GET /login` | 누구나 | 로그인 화면 (`POST /login` 은 Spring Security 가 처리) |
| `GET, POST /signup` | 누구나 | 회원가입 |
| `GET /me` | 로그인 사용자 | 내 정보 |
| `GET /admin/members` | `ADMIN` | 전체 회원 목록 |
| `POST /logout` | 로그인 사용자 | 로그아웃 |

### REST API (JWT Bearer 토큰)

| Method & URL | 접근 권한 | 설명 |
|--------------|-----------|------|
| `POST /api/members` | 누구나 | 회원가입 |
| `POST /api/auth/token` | 누구나 | 아이디/비밀번호로 JWT 발급 |
| `GET /api/me` | 토큰 보유자 | 내 정보 |
| `GET /api/members/{username}` | 본인 또는 `ADMIN` | 회원 조회 (`@PreAuthorize`) |
| `GET /api/admin/members` | `ADMIN` | 전체 회원 목록 |

## 프로젝트 구조

```
hello-spring-security
├── build.gradle.kts / settings.gradle.kts
├── compose.yaml                         # PostgreSQL 18.6
├── docs/                                # 강의 문서 (00 ~ 10)
└── src
    ├── main
    │   ├── java/com/example/security
    │   │   ├── HelloSpringSecurityApplication.java
    │   │   ├── config/                  # SecurityFilterChain 설정 (웹 / API)
    │   │   ├── auth/                    # UserDetails, UserDetailsService
    │   │   ├── member/                  # 회원 도메인 (Entity, Repository, Service)
    │   │   ├── jwt/                     # JWT 발급/검증 설정
    │   │   ├── web/                     # Thymeleaf 화면 컨트롤러
    │   │   └── api/                     # REST API 컨트롤러
    │   └── resources
    │       ├── application.yml
    │       ├── static/css/style.css
    │       └── templates/               # Thymeleaf 템플릿
    └── test/java/com/example/security    # 단위 / 통합 테스트
```

## 문서 읽는 방법

- 각 문서는 **학습 목표 → 개념 → 코드 살펴보기 → 직접 해보기 → 정리** 순서로 구성되어 있습니다.
- **직접 해보기**의 명령어는 애플리케이션이 `./gradlew bootRun` 으로 실행 중이라고 가정합니다.
- 문서 하단의 링크로 이전/다음 문서로 이동할 수 있습니다.

---

| 이전 | 목차 | 다음 |
|:-----|:----:|-----:|
| | **README** | [00. 실습 환경 준비 →](docs/00-setup.md) |
