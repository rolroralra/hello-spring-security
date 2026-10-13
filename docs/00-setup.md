# 00. 실습 환경 준비

## 학습 목표

- 실습 프로젝트를 내려받아 실행할 수 있다.
- Docker Compose 로 PostgreSQL 이 어떻게 함께 실행되는지 이해한다.
- 프로젝트의 의존성과 디렉토리 구조를 파악한다.

## 1. 준비물

| 도구 | 비고 |
|------|------|
| JDK | **설치하지 않아도 된다.** Gradle Toolchain 이 JDK 25 를 자동으로 내려받는다. (IDE 에서 실행하려면 JDK 25 를 설치해 두는 것이 편하다) |
| Docker Desktop | PostgreSQL 컨테이너 실행과 Testcontainers 테스트에 필요하다. |
| IDE | IntelliJ IDEA 권장 |

JDK 25 가 없는데 어떻게 빌드가 될까? `settings.gradle.kts` 에 추가한 플러그인 덕분이다.

```kotlin
// settings.gradle.kts
plugins {
    // 로컬에 JDK 25가 없으면 Gradle이 자동으로 내려받는다 (Java Toolchain auto-provisioning)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
```

```kotlin
// build.gradle.kts
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

Gradle 은 `toolchain` 에 지정된 버전의 JDK 를 로컬에서 찾고, 없으면 foojay API 를 통해 내려받아 `~/.gradle/jdks` 에 설치한다.
그래서 시스템 기본 Java 가 21 이어도 이 프로젝트는 Java 25 로 컴파일되고 실행된다.

## 2. 의존성 살펴보기

[`build.gradle.kts`](../build.gradle.kts) 를 열어 보자.

```kotlin
dependencies {
    // Web + View
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // bootRun 시 compose.yaml 의 PostgreSQL 컨테이너를 자동으로 띄운다
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    ...
}
```

| 의존성 | 역할 | 등장하는 장 |
|--------|------|-------------|
| `spring-boot-starter-security` | Spring Security 핵심 | 02 ~ |
| `spring-boot-starter-security-oauth2-resource-server` | JWT Bearer 토큰 검증 | 09 |
| `thymeleaf-extras-springsecurity6` | 템플릿에서 `sec:authorize` 등 사용 | 06 |
| `spring-boot-starter-data-jpa` + `postgresql` | 회원 정보 저장 | 05 |
| `spring-boot-docker-compose` | `bootRun` 시 `compose.yaml` 자동 실행 | 00 |
| `spring-boot-starter-security-test` | `@WithMockUser`, `csrf()` 등 테스트 지원 | 10 |

> **Spring Boot 4 의 변화**
> Spring Boot 4 부터 스타터가 기술별로 잘게 나뉘었다. 예전의 `spring-boot-starter-web` 은 `spring-boot-starter-webmvc` 가 되었고,
> 테스트 지원도 `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test` 처럼 기능별 테스트 스타터로 분리되었다.

## 3. PostgreSQL 을 Docker Compose 로 띄우기

[`compose.yaml`](../compose.yaml)

```yaml
services:
  postgres:
    image: postgres:18.6-alpine
    container_name: hello-security-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-hello_security}
      POSTGRES_USER: ${POSTGRES_USER:-hello}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-hello1234}
      TZ: Asia/Seoul
    ports:
      - "${POSTGRES_PORT:-15432}:5432"
    volumes:
      - postgres-data:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
```

알아둘 점:

- **버전 고정**: `latest` 대신 `18.6-alpine` 처럼 버전을 명시한다. 언제 실행해도 같은 환경이 보장된다.
- **호스트 포트 15432**: 로컬에 이미 5432 포트를 쓰는 PostgreSQL 이 있어도 충돌하지 않도록 했다. `POSTGRES_PORT` 환경 변수로 바꿀 수 있다.
- **볼륨 경로**: PostgreSQL 18 공식 이미지부터 데이터 디렉토리가 `/var/lib/postgresql/18/docker` 로 바뀌었다.
  그래서 예전처럼 `/var/lib/postgresql/data` 가 아니라 상위 디렉토리인 `/var/lib/postgresql` 을 마운트한다.

### spring-boot-docker-compose 의 마법

`./gradlew bootRun` 을 실행하면 Spring Boot 가 프로젝트 루트의 `compose.yaml` 을 발견하고 다음을 자동으로 처리한다.

1. `docker compose up` 으로 컨테이너를 띄우고 healthcheck 가 통과할 때까지 기다린다.
2. 컨테이너의 접속 정보(호스트, 포트, DB 이름, 계정)를 읽어 **DataSource 설정을 자동으로 덮어쓴다** (ServiceConnection).
3. `application.yml` 의 `spring.docker.compose.lifecycle-management: start-only` 설정 때문에 애플리케이션을 종료해도 컨테이너는 계속 떠 있다. 다음 실행이 빨라진다.

이 의존성은 `developmentOnly` 이므로 `bootJar` 로 만든 실행 파일에는 포함되지 않는다.

## 4. application.yml

[`src/main/resources/application.yml`](../src/main/resources/application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:${POSTGRES_PORT:15432}/${POSTGRES_DB:hello_security}
    username: ${POSTGRES_USER:hello}
    password: ${POSTGRES_PASSWORD:hello1234}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: update        # 학습용. 운영에서는 validate + Flyway/Liquibase

app:
  admin:                      # 기동 시 자동 생성되는 관리자 계정
    username: ${APP_ADMIN_USERNAME:admin}
    password: ${APP_ADMIN_PASSWORD:admin1234}
```

`datasource` 값은 Docker Compose 지원이 꺼져 있을 때(예: `java -jar` 로 실행)를 위한 기본값이다.
비밀번호 같은 값은 `${ENV_NAME:기본값}` 형태로 환경 변수에서 읽도록 해 두었다. **기본값은 로컬 학습용일 뿐, 운영에서는 반드시 환경 변수로 주입**해야 한다.

## 5. 실행하기

### 방법 A. bootRun (권장)

```bash
./gradlew bootRun
```

로그에서 다음 두 줄을 찾아보자.

```
Started HelloSpringSecurityApplication in 9.22 seconds
관리자 계정을 생성했습니다: admin
```

컨테이너도 확인한다.

```bash
docker ps --filter name=hello-security
# hello-security-postgres  postgres:18.6-alpine  0.0.0.0:15432->5432/tcp  Up (healthy)
```

### 방법 B. Docker Compose 를 직접 실행

```bash
docker compose up -d
./gradlew bootRun
```

### 방법 C. Testcontainers 로 실행

```bash
./gradlew bootTestRun
```

`src/test/java/.../TestHelloSpringSecurityApplication.java` 가 Testcontainers 로 **일회용** PostgreSQL 을 띄워서 애플리케이션을 실행한다.
종료하면 데이터도 사라진다.

### 접속 확인

- http://localhost:8080 → 홈 화면
- 관리자 계정: `admin` / `admin1234`

DB 에 직접 접속해 보고 싶다면:

```bash
docker exec -it hello-security-postgres psql -U hello -d hello_security
hello_security=# \dt
hello_security=# select id, username, role from members;
```

## 6. 테스트 실행

```bash
./gradlew test
```

통합 테스트는 Testcontainers 로 PostgreSQL 컨테이너를 띄우므로 **Docker Desktop 이 실행 중이어야 한다.**
결과 리포트는 `build/reports/tests/test/index.html` 에서 볼 수 있다.

## 7. 자주 만나는 문제

| 증상 | 원인 / 해결 |
|------|-------------|
| `Bind for 0.0.0.0:15432 failed: port is already allocated` | 해당 포트를 다른 프로세스가 사용 중. `POSTGRES_PORT=25432 ./gradlew bootRun` 처럼 다른 포트를 지정한다. |
| `Could not find a valid Docker environment` (테스트) | Docker Desktop 이 꺼져 있다. 실행 후 다시 시도한다. |
| `Cannot find a Java installation ... languageVersion=25` | 네트워크 문제로 JDK 자동 다운로드 실패. JDK 25 를 직접 설치하거나 네트워크를 확인한다. |
| 로그인이 안 된다 | DB 볼륨에 예전 데이터가 남아 있을 수 있다. `docker compose down -v` 로 볼륨까지 지우고 다시 실행한다. |

## 정리

- Gradle Toolchain 덕분에 JDK 25 를 별도로 설치하지 않아도 된다.
- `spring-boot-docker-compose` 가 `bootRun` 시 PostgreSQL 컨테이너를 띄우고 접속 정보까지 연결해 준다.
- 테스트는 Testcontainers 가 별도의 PostgreSQL 을 띄워서 실행한다.

---

| 이전 | 목차 | 다음 |
|:-----|:----:|-----:|
| | [목차](../README.md) | [01. Spring Security 핵심 개념 →](01-concepts.md) |
