# 04. PasswordEncoder - 비밀번호를 안전하게 저장하기

## 학습 목표

- 비밀번호를 암호화가 아니라 **해시**해서 저장하는 이유를 설명할 수 있다.
- Salt 와 BCrypt 의 work factor 가 무엇을 막아 주는지 안다.
- `DelegatingPasswordEncoder` 의 `{bcrypt}` 접두사가 왜 필요한지 안다.

## 1. 평문 저장, 암호화, 해시

DB 가 유출되었다고 가정하자.

| 저장 방식 | 저장된 값 | 유출 시 |
|-----------|-----------|---------|
| 평문 | `password1234` | 그대로 노출. 최악. |
| 암호화 (AES 등) | `U2FsdGVkX1...` | 키도 같이 털리면 전부 복호화된다. 복호화가 **가능**하다는 것 자체가 위험. |
| 단순 해시 (SHA-256) | `b9c950640e...` | 되돌릴 수는 없지만, 같은 비밀번호는 같은 해시 → 미리 계산한 표(레인보우 테이블)로 순식간에 역추적. GPU 로 초당 수십억 번 대입 가능. |
| **Salt + 느린 해시 (BCrypt)** | `$2a$10$o4MLJ...` | 사용자마다 다른 salt → 레인보우 테이블 무력화. 일부러 느리게 → 무차별 대입 비용 폭증. |

로그인할 때는 "입력값을 같은 방식으로 해시해서 저장된 값과 같은지" 만 비교하면 되므로 **원래 비밀번호를 복원할 필요가 전혀 없다.** 그래서 해시를 쓴다.

### BCrypt 해시 읽는 법

```
$2a$10$o4MLJ7Pz0p4m3Qy0eX1Q2uW8u3q9...
 │   │  └────────── salt (22자) ─────────┘└─ 해시 결과 ─┘
 │   └─ cost(work factor) = 10  → 2^10 = 1024 번 반복
 └─ 알고리즘 버전
```

- **salt 가 해시 안에 들어 있다.** 그래서 별도 컬럼이 필요 없다.
- cost 를 1 올리면 계산 시간이 2배가 된다. 하드웨어가 빨라지면 cost 를 올리면 된다.

## 2. PasswordEncoder 인터페이스

```java
public interface PasswordEncoder {
    String encode(CharSequence rawPassword);                              // 저장할 때
    boolean matches(CharSequence rawPassword, String encodedPassword);    // 로그인할 때
    default boolean upgradeEncoding(String encodedPassword) { ... }       // 더 강한 방식으로 바꿔야 하나?
}
```

## 3. 우리 프로젝트의 선택: DelegatingPasswordEncoder

[`SecurityConfig.java`](../src/main/java/com/example/security/config/SecurityConfig.java)

```java
@Bean
public PasswordEncoder passwordEncoder() {
    // {bcrypt}$2a$10$... 처럼 알고리즘 id 를 접두사로 붙여 저장한다
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```

그냥 `new BCryptPasswordEncoder()` 를 쓰지 않는 이유는 **미래의 알고리즘 교체** 때문이다.

```
{bcrypt}$2a$10$o4MLJ...      ← 지금 가입한 사용자
{argon2}$argon2id$v=19$...   ← 나중에 기본 알고리즘을 바꾼 뒤 가입한 사용자
{noop}password1234           ← 테스트용 평문 (운영 금지)
```

`DelegatingPasswordEncoder` 는 `{id}` 접두사를 보고 알맞은 인코더에게 비교를 맡긴다.
덕분에 알고리즘을 바꿔도 **기존 사용자의 비밀번호를 한 번에 바꿀 필요가 없다.** 옛 방식 해시도 계속 검증할 수 있고,
`upgradeEncoding()` 이 `true` 인 사용자는 다음 로그인 때 새 방식으로 다시 저장할 수 있다.

## 4. 직접 확인하기 - 학습 테스트

[`PasswordEncoderTest.java`](../src/test/java/com/example/security/auth/PasswordEncoderTest.java) 는 PasswordEncoder 의 성질을 확인하는 **학습 테스트**다.

```java
@Test
void 같은_비밀번호라도_salt_때문에_매번_다른_값이_나온다() {
    String first = encoder.encode("password1234");
    String second = encoder.encode("password1234");

    assertThat(first).isNotEqualTo(second);                      // 값은 다르지만
    assertThat(encoder.matches("password1234", first)).isTrue(); // 둘 다 검증은 통과
    assertThat(encoder.matches("password1234", second)).isTrue();
}

@Test
void 접두사로_알고리즘을_선택하므로_다른_인코더로_만든_해시도_검증할_수_있다() {
    String legacyBcrypt = "{bcrypt}" + new BCryptPasswordEncoder(4).encode("password1234");

    assertThat(encoder.matches("password1234", legacyBcrypt)).isTrue();
    assertThat(encoder.upgradeEncoding(legacyBcrypt)).isTrue();  // cost 4 < 10 이므로 업그레이드 대상
}
```

Docker 없이도 실행된다.

```bash
./gradlew test --tests '*PasswordEncoderTest'
```

## 5. 실제 DB 에 저장된 값 보기

```bash
docker exec hello-security-postgres psql -U hello -d hello_security \
  -c "select id, username, left(password, 20) as password, role from members"
```

```
 id | username |       password       | role
----+----------+----------------------+-------
  1 | admin    | {bcrypt}$2a$10$o4MLJ | ADMIN
  2 | alice    | {bcrypt}$2a$10$oJfqC | USER
```

비밀번호를 인코딩하는 곳은 [`MemberService#register`](../src/main/java/com/example/security/member/MemberService.java) 한 곳뿐이다.

```java
Member member = Member.create(username, passwordEncoder.encode(rawPassword), nickname, role);
```

## 6. 비밀번호를 다룰 때 지킬 것

- **평문 비밀번호를 로그에 남기지 않는다.** 이 프로젝트의 `SignupRequest`, `TokenRequest` 는 `toString()` 을 재정의해서 비밀번호를 빼고 출력한다.
- **응답에 해시도 내보내지 않는다.** API 응답용 `MemberResponse` 에는 password 필드가 아예 없다.
- `{noop}` 은 테스트 코드에서만 쓴다.
- 비밀번호 비교를 `equals()` 로 직접 하지 않는다. 항상 `PasswordEncoder#matches` 를 쓴다.

## 직접 해보기

1. 회원가입 화면에서 **같은 비밀번호**로 계정 두 개를 만들고, DB 에 저장된 해시가 서로 다른지 확인해 보자.
2. `PasswordEncoderTest` 에 cost 를 4, 10, 14 로 바꿔가며 `encode()` 시간을 재는 테스트를 추가해 보자. cost 가 1 오를 때마다 시간이 대략 두 배가 되는가?

## 정리

- 비밀번호는 복원할 필요가 없으므로 **해시**한다. 그중에서도 salt 를 쓰고 일부러 느린 BCrypt 같은 알고리즘을 쓴다.
- `DelegatingPasswordEncoder` 는 `{id}` 접두사로 알고리즘을 구분해서, 나중에 알고리즘을 바꾸기 쉽게 해준다.
- 평문 비밀번호와 해시는 로그와 응답 어디에도 노출하지 않는다.

---

| 이전 | 목차 | 다음 |
|:-----|:----:|-----:|
| [← 03. 아키텍처 - 필터 체인](03-architecture.md) | [목차](../README.md) | [05. UserDetailsService 와 JPA 회원 →](05-userdetails-jpa.md) |
