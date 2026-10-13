# 02. 자동 구성 - 아무것도 안 했는데 로그인 화면이?

## 학습 목표

- `spring-boot-starter-security` 만 추가했을 때 어떤 일이 일어나는지 안다.
- Spring Boot 의 기본 보안 설정을 **우리 설정으로 교체**하는 원리를 이해한다.

## 1. 실험: 빈 프로젝트에 Security 만 추가하기

이 장은 우리 프로젝트가 아닌 **새 프로젝트**로 실험한다. 5분이면 된다.

```bash
cd /tmp
curl https://start.spring.io/starter.zip \
  -d type=gradle-project-kotlin -d bootVersion=4.1.1 -d javaVersion=25 \
  -d dependencies=web,security -o security-demo.zip
unzip security-demo.zip -d security-demo && cd security-demo
```

`src/main/java/com/example/demo/DemoApplication.java` 옆에 컨트롤러를 하나 추가한다.

```java
@RestController
class HelloController {
    @GetMapping("/hello")
    String hello() {
        return "hello";
    }
}
```

실행한다.

```bash
./gradlew bootRun
```

### 관찰 1. 로그에 비밀번호가 찍힌다

```
Using generated security password: 4721285b-ff49-407c-aa29-d06c85fc7ba5

This generated password is for development use only. Your security configuration must be updated before running your application in production.
```

### 관찰 2. 모든 요청이 막힌다

```bash
curl -i http://localhost:8080/hello
# HTTP/1.1 401
# WWW-Authenticate: Basic realm="Realm", charset="UTF-8"
```

브라우저로 http://localhost:8080/hello 에 접속하면 **로그인 화면으로 이동**한다. 같은 URL 인데 반응이 다르다!
Spring Security 가 요청의 `Accept` 헤더를 보고 브라우저면 로그인 페이지로, 그 외(curl 등)는 401 + HTTP Basic 인증 요구로 응답하기 때문이다.

### 관찰 3. 아이디 `user` + 로그의 비밀번호로 로그인된다

```bash
curl -u user:4721285b-ff49-407c-aa29-d06c85fc7ba5 http://localhost:8080/hello   # 비밀번호는 각자 로그에 찍힌 값
# hello
```

브라우저에서 로그인한 뒤 http://localhost:8080/logout 에 접속하면 로그아웃 확인 화면도 나온다.

## 2. 누가 이걸 해줬을까?

Spring Boot 의 자동 구성(Auto Configuration)이다. 핵심만 보면 두 가지를 만들어 준다.

### (1) 기본 SecurityFilterChain

**개발자가 `SecurityFilterChain` 빈을 하나도 등록하지 않았을 때만** 아래와 같은 기본 설정이 등록된다.

```java
// Spring Boot 가 내부적으로 등록하는 기본 설정 (개념상 동일한 코드)
@Bean
SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated()); // 모든 요청 인증 필요
    http.formLogin(withDefaults());                                                 // 기본 로그인 페이지
    http.httpBasic(withDefaults());                                                 // HTTP Basic 인증
    return http.build();
}
```

### (2) 메모리 사용자 1명

**개발자가 `UserDetailsService`, `AuthenticationProvider`, `AuthenticationManager` 빈을 등록하지 않았을 때만**
`InMemoryUserDetailsManager` 에 `user` / 랜덤 비밀번호 계정을 만들어 준다.
`application.yml` 로 바꿀 수도 있다.

```yaml
spring:
  security:
    user:
      name: tester
      password: test1234
      roles: USER
```

그 밖에도 CSRF 방어, 보안 응답 헤더(`X-Frame-Options`, `X-Content-Type-Options`, `Cache-Control` 등), 세션 고정 공격 방어가 **기본으로 켜져 있다.**
"일단 다 막고 시작한다(secure by default)" 가 Spring Security 의 철학이다.

## 3. 기본 설정을 교체하는 원리

자동 구성은 "개발자가 직접 만들지 않았다면" 이라는 조건(`@ConditionalOnMissingBean` 류)이 붙어 있다.
그래서 **같은 종류의 빈을 직접 등록하면 자동 구성은 물러난다(back off).**

| 직접 등록한 빈 | 물러나는 자동 구성 | 우리 프로젝트의 위치 |
|---------------|-------------------|----------------------|
| `SecurityFilterChain` | 기본 필터 체인 | `config/SecurityConfig`, `config/ApiSecurityConfig` |
| `UserDetailsService` | `user` / 랜덤 비밀번호 계정 | `auth/MemberUserDetailsService` |

우리 프로젝트를 `./gradlew bootRun` 으로 실행하면 `Using generated security password` 로그가 **나오지 않는다.**
`MemberUserDetailsService` 를 등록했기 때문이다.

[`SecurityConfig`](../src/main/java/com/example/security/config/SecurityConfig.java) 의 핵심 부분을 미리 보자.

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/signup", "/error", "/access-denied").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/me")
                        .permitAll()
                )
                ...;
        return http.build();
    }
}
```

기본 설정과 비교하면:

- 홈, 로그인, 회원가입 페이지는 **누구나** 접근 가능 (`permitAll`)
- `/admin/**` 은 **ADMIN 만**
- 기본 로그인 페이지 대신 **우리가 만든 로그인 페이지** (`/login`) 사용
- HTTP Basic 은 쓰지 않음

`HttpSecurity` 의 각 설정은 **람다 DSL** 로 작성한다. 예전 글에서 볼 수 있는 `.and()` 체이닝 방식은 Spring Security 7 에서 제거되었으므로,
인터넷 예제를 참고할 때는 버전을 꼭 확인하자.

## 직접 해보기

1. 실험 프로젝트에서 `application.yml` 에 `spring.security.user.name/password` 를 설정하고 그 계정으로 로그인해 보자.
2. 실험 프로젝트에 아래 설정을 추가해서 `/hello` 를 누구나 볼 수 있게 만들어 보자. 추가하는 순간 기본 로그인 페이지도 사라진다. 왜일까?

```java
@Configuration
class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/hello").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
```

<details>
<summary>2번 해설</summary>

`SecurityFilterChain` 빈을 직접 등록했으므로 Spring Boot 의 기본 체인(`formLogin` + `httpBasic` 포함)이 등록되지 않는다.
우리 체인에는 `formLogin()` 을 설정하지 않았으므로 로그인 페이지도 없다. 보호된 URL 에 접근하면 403 이 된다.
"설정은 덧붙이는 것이 아니라 **교체**하는 것" 이라는 점을 기억하자.

</details>

## 정리

- `spring-boot-starter-security` 만 추가해도 모든 요청이 인증을 요구하고, 기본 로그인 페이지와 `user` 계정이 생긴다.
- `SecurityFilterChain`, `UserDetailsService` 빈을 직접 등록하면 해당 자동 구성은 물러난다.
- 설정은 람다 DSL 로 작성한다.

---

| 이전 | 목차 | 다음 |
|:-----|:----:|-----:|
| [← 01. Spring Security 핵심 개념](01-concepts.md) | [목차](../README.md) | [03. 아키텍처 - 필터 체인 →](03-architecture.md) |
