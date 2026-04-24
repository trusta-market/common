# Trusta Common

Trusta MSA 프로젝트의 **공통 라이브러리**. 모든 도메인 서비스에서 의존성으로 추가해 사용한다.

- **패키지 베이스**: `com.trustamarket.common`
- **Artifact**: `com.trustamarket:common:0.0.1-SNAPSHOT`
- **배포**: GitHub Packages (`trusta-market/common`)
- **Java**: 21 / **Spring Boot**: 3.5.13 / **Spring Cloud**: 2025.0.1

> API 응답은 `{status, data, pageInfo?}` 구조이며, 에러 응답은 **RFC 9457 Problem Details**를 엄격히 준수한다.

---

## 📑 목차

1. [기술 스택](#기술-스택)
2. [패키지 구조](#패키지-구조)
3. [프로젝트 초기화 (최초 1회, 참고용)](#프로젝트-초기화-최초-1회-참고용)
4. [배포하기 (관리자)](#배포하기-관리자)
5. [사용하기 (도메인 서비스)](#사용하기-도메인-서비스)
6. [자동 등록되는 기능](#자동-등록되는-기능)
7. [공통 응답 포맷](#공통-응답-포맷)
8. [공통 예외](#공통-예외)
9. [공통 엔티티](#공통-엔티티)
10. [Outbox / Inbox 패턴](#outbox-패턴--kafka-이벤트-발행)
11. [MDC 트레이싱 / Feign / Pageable / Security / Util](#mdc-트레이싱)

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.13, Spring Cloud 2025.0.1 |
| 계층 | Spring MVC (Servlet) |
| ORM | JPA + QueryDSL 5.1.0 (Auditing, Soft Delete, EntityScan) |
| Messaging | Kafka (Outbox / Inbox 패턴, DLT) |
| 서비스 간 통신 | OpenFeign (헤더 자동 전파) |
| Security | Keycloak + JWT (Gateway → 서비스 헤더 전파) |
| 보일러 제거 | Lombok |
| 빌드 | Gradle (`java-library` + `maven-publish`) |
| 배포 | GitHub Packages |

---

## 패키지 구조

```text
com.trustamarket.common
├── config/                                   # Spring 자동 설정 — Bean 등록 진입점
│   ├── CommonAutoConfiguration.java          # @AutoConfiguration. 모든 빈 수동 등록 진입점
│   ├── JpaConfig.java                        # JPA Auditing + EntityScan + QueryDSL. DB 설정 시만 활성
│   ├── JsonConfig.java                       # ObjectMapper (JavaTimeModule, ISO 8601) + JsonUtil
│   ├── EventConfig.java                      # @Async + @Scheduling, ThreadPool + MdcTaskDecorator
│   ├── feign/FeignConfig.java                # Feign RequestInterceptor — 헤더 자동 전파
│   ├── security/
│   │   ├── SecurityConfig.java               # stateless, LoginFilter 등록, @EnableMethodSecurity
│   │   ├── LoginFilter.java                  # X-User-* 헤더 → SecurityContext 주입
│   │   ├── UserDetailsImpl.java              # 인증 주체 VO (UUID, 이메일, 이름, 권한 등)
│   │   ├── CustomAuthenticationEntryPoint.java  # 401 핸들러
│   │   └── CustomAccessDeniedHandler.java    # 403 핸들러
│   └── web/
│       ├── WebConfig.java                    # Resolver 등록
│       └── RestrictedPageableResolver.java   # size 10/30/50 만 허용
│
├── response/                                 # Trusta 표준 응답 포맷
│   ├── CommonResponse.java                   # record. { status, data } — 단일/일반 응답
│   ├── PagedResponse.java                    # record. { status, data, pageInfo } — 페이지 응답
│   ├── PageInfo.java                         # record + @Builder. Page 메타 요약
│   ├── SlicedResponse.java                   # record. { status, data, sliceInfo } — 슬라이스 응답
│   ├── SliceInfo.java                        # record + @Builder. Slice 메타 (COUNT 쿼리 없음)
│   ├── ErrorResponse.java                    # record. RFC 9457 엄격 (type/title/status/detail/instance)
│   └── CommonResponseAdvice.java             # 컨트롤러 반환값 자동 래핑. Page / Slice 자동 감지
│
├── exception/                                # 전역 예외 계층
│   ├── CustomException.java                  # 공통 베이스. type/status/field 필드
│   ├── ErrorCodeSpec.java                    # 도메인별 ErrorCode enum 확장 인터페이스
│   ├── BadRequestException.java              # 400
│   ├── UnauthorizedException.java            # 401
│   ├── ForbiddenException.java               # 403
│   ├── NotFoundException.java                # 404
│   ├── ConflictException.java                # 409
│   ├── InternalServerException.java          # 500
│   └── GlobalExceptionAdvice.java            # 모든 예외 → ErrorResponse (RFC 9457)
│
├── domain/                                   # JPA 공통 엔티티
│   ├── BaseEntity.java                       # createdAt / updatedAt / deletedAt + idempotent soft delete
│   ├── BaseUserEntity.java                   # + createdBy / updatedBy / deletedBy (UUID), idempotent delete
│   ├── outbox/
│   │   ├── Outbox.java                       # P_OUTBOX 엔티티. markDltPending()/markDltSent() 상태 전이
│   │   ├── OutboxStatus.java                 # PENDING / PROCESSED / FAILED / DLT_PENDING / DLT_SENT
│   │   └── OutboxRepository.java             # 메인 재시도 + DLT 재시도(findByStatus) + correlationId 조회
│   └── inbox/
│       ├── Inbox.java                        # P_INBOX 엔티티. 복합 PK (messageId, messageGroup)
│       ├── InboxId.java                      # IdClass 복합 PK — 컨슈머 그룹별 독립 멱등성
│       └── InboxRepository.java              # 오래된 레코드 일괄 삭제 쿼리 (clearAutomatically)
│
├── event/                                    # Outbox 이벤트 발행
│   ├── OutboxEvent.java                      # record 이벤트 VO
│   ├── Events.java                           # 정적 trigger() — 주입 없이 발행, publisher null 시 warn 로그
│   ├── OutboxEventListener.java              # DB 저장(unique 제약) → Kafka 발행 (AFTER_COMMIT)
│   ├── OutboxCallback.java                   # 성공/실패 콜백 (REQUIRES_NEW) + DLT 격리 커밋
│   ├── OutboxDltAckHandler.java              # DLT ack 성공 시 DLT_SENT 전이 (REQUIRES_NEW 전담 빈)
│   └── OutboxRelayScheduler.java             # 메인/DLT 재시도 10초 주기 분기
│
├── messaging/                                # Inbox 멱등 소비
│   ├── IdempotentConsumer.java               # @IdempotentConsumer 애노테이션
│   ├── InboxAdvice.java                      # AOP. message_id 헤더 기반 중복 방지
│   └── InboxCleanupScheduler.java            # 매일 03:00, 7일 지난 Inbox 일괄 삭제
│
├── filter/
│   └── MdcLoggingFilter.java                 # X-Trace-Id 생성/전파, MDC 주입
│
└── util/
    ├── JsonUtil.java                         # ObjectMapper 래퍼. 런타임 예외로 승격
    ├── SecurityUtil.java                     # 현재 사용자 UUID/이메일 조회 정적 헬퍼
    └── MdcTaskDecorator.java                 # @Async 스레드에 MDC 전파

resources/
└── META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## 프로젝트 초기화 (최초 1회, 참고용)

> 이 라이브러리는 이미 초기화되어 있다. 아래는 처음부터 다시 만들 때의 절차 기록용.

### 방식 A — Spring Initializr (권장)

**웹 UI**: https://start.spring.io/

| 필드 | 값 |
|---|---|
| Project | Gradle - Groovy |
| Language | Java |
| Spring Boot | 3.5.13 |
| Group | `com.trustamarket` |
| Artifact | `common` |
| Name | `common` |
| Package name | `com.trustamarket.common` |
| Packaging | Jar |
| Java | 21 |
| Dependencies | Spring Web, Spring Data JPA, Validation, Spring Security, Lombok, Spring for Apache Kafka, OpenFeign |

**CLI** (Homebrew Spring Boot CLI):

```bash
brew tap spring-io/tap
brew install spring-boot

spring init \
  --type=gradle-project \
  --boot-version=3.5.13 \
  --java-version=21 \
  --group=com.trustamarket \
  --artifact=common \
  --name=common \
  --package-name=com.trustamarket.common \
  --dependencies=web,data-jpa,validation,security,lombok,kafka,cloud-feign \
  trusta-common
```

### 방식 B — 순수 Gradle init

```bash
mkdir trusta-common && cd trusta-common
gradle init \
  --type java-library \
  --dsl groovy \
  --test-framework junit-jupiter \
  --project-name trusta-common \
  --package com.trustamarket.common
```

Spring Boot 플러그인과 의존성은 이후 `build.gradle` 에 수동 추가.

### 라이브러리화 — build.gradle 수정

생성된 프로젝트를 **실행형 애플리케이션이 아닌 라이브러리**로 바꾸려면 `build.gradle` 을 아래처럼 조정한다.

```groovy
plugins {
    id 'java-library'          // 추가 — api/implementation 분리
    id 'maven-publish'         // 추가 — GitHub Packages 배포
    id 'org.springframework.boot' version '3.5.13'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.trustamarket'
version = '0.0.1-SNAPSHOT'

// 라이브러리이므로 bootJar 끄고, 일반 jar 사용
bootJar { enabled = false }
jar { enabled = true }

dependencies {
    api 'org.springframework.boot:spring-boot-starter-web'
    api 'org.springframework.boot:spring-boot-starter-data-jpa'
    // ... (소비자가 transitive 로 받아야 하는 건 api, 아니면 implementation)
}
```

> 자동 설정을 위한 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
> 파일에 `com.trustamarket.common.config.CommonAutoConfiguration` 를 한 줄 기재해야 소비자 서비스가
> 의존성만 추가해도 빈이 자동 등록된다.

---

## 배포하기 (관리자)

### 전체 흐름

```text
[최초 1회] trusta-market/common GitHub 레포 생성
       ↓
[최초 1회] PAT (write:packages) 발급 + gradle.properties 설정
       ↓
코드 변경 → version 조정 → ./gradlew publish
       ↓
GitHub Packages 에 .jar + pom.xml 업로드
       ↓
소비자 서비스가 의존성으로 당겨 사용
```

### Step 1. GitHub 레포 생성 (최초 1회)

`trusta-market` 조직에서 **common** 이라는 private 레포 생성.
- URL: `https://github.com/trusta-market/common`
- 공개/비공개 여부는 조직 정책에 맞춰 결정 (기본은 private 권장)

### Step 2. PAT 발급 (최초 1회)

GitHub → Settings → Developer settings → Personal access tokens → **Tokens (classic)**

| 스코프 | 용도 |
|---|---|
| `write:packages` | **배포**용. common 을 publish 할 때 필수 |
| `read:packages` | 소비자 서비스가 의존성 받을 때 |
| `delete:packages` | 과거 버전 정리 (선택) |

> SSO 가 걸린 조직이면 토큰 발급 직후 **Configure SSO → Authorize** 까지 눌러야 한다.

### Step 3. 인증 설정

`~/.gradle/gradle.properties` (**프로젝트 루트 아님 — 홈 디렉토리**):

```properties
gpr.user=<GitHub 아이디>
gpr.token=<발급받은 토큰>
```

또는 환경변수:

```bash
export GPR_USER=<GitHub 아이디>
export GPR_TOKEN=<발급받은 토큰>
```

`build.gradle` 은 `findProperty('gpr.user') ?: System.getenv('GPR_USER')` 순으로 읽으므로 둘 중 편한 쪽만 설정하면 된다.

### Step 4. 버전 정책

- **개발 중**: `0.0.1-SNAPSHOT` — 같은 버전에 덮어쓰기 가능. 소비자는 매 빌드마다 최신 SNAPSHOT 을 받는다
- **릴리스**: `0.0.1`, `0.0.2` — 불변. 같은 버전 재배포 불가
- `build.gradle` 의 `version` 값만 올리면 됨

### Step 5. Publish

```bash
# 컴파일/테스트 검증 후
./gradlew clean build

# GitHub Packages 로 배포
./gradlew publish
```

성공하면 https://github.com/orgs/trusta-market/packages 에 `com.trustamarket.common` 이 보인다.

**로컬 전용** (소비자 서비스가 로컬 개발에서 참조할 때):
```bash
./gradlew publishToMavenLocal
# → ~/.m2/repository/com/trustamarket/common/ 에 설치됨
```

### 트러블슈팅

| 증상 | 원인 | 조치 |
|---|---|---|
| `401 Unauthorized` | 토큰/스코프 부족 | PAT 에 `write:packages` 포함, SSO 승인 확인 |
| `422 Unprocessable Entity` | 같은 릴리스 버전 중복 배포 | version 을 올리거나 `-SNAPSHOT` 사용 |
| `Could not find com.trustamarket:common` (소비자) | `read:packages` 누락 or repository url 오타 | PAT 스코프 재확인, `maven { url ... }` 확인 |

---

## 사용하기 (도메인 서비스)

### Step 1. PAT 설정 (최초 1회)

`~/.gradle/gradle.properties`:

```properties
gpr.user=<GitHub 아이디>
gpr.token=<read:packages 스코프 PAT>
```

### Step 2. `build.gradle` 에 의존성 추가

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/trusta-market/common")
        credentials {
            username = findProperty('gpr.user') ?: System.getenv('GPR_USER')
            password = findProperty('gpr.token') ?: System.getenv('GPR_TOKEN')
        }
    }
}

dependencies {
    implementation 'com.trustamarket:common:0.0.1-SNAPSHOT'
}
```

### Step 3. 끝

의존성만 추가하면 `CommonAutoConfiguration` 이 자동으로 모든 빈을 등록한다. 별도 `@Import` / `@ComponentScan` 불필요.

### 도메인 서비스 `application.yaml` 예시

```yaml
server:
  port: 18081

spring:
  application:
    name: order-service
  datasource:
    url: jdbc:postgresql://localhost:5432/order_db
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true
  kafka:
    bootstrap-servers: <Kafka brokers>

trusta:
  security:
    # Gateway 뒤 배치된 서비스에서만 true. false(default) 면 LoginFilter 비활성 → 인증 필요한 엔드포인트는 401.
    trust-gateway-headers: true
  messaging:
    outbox:
      enabled: true    # Kafka 이벤트 발행 (Outbox 패턴) 이 필요한 서비스만 true. default false
    inbox:
      enabled: true    # Kafka 멱등 소비 (@IdempotentConsumer) 가 필요한 서비스만 true. default false
```

### Trusta 프로퍼티 요약

| 프로퍼티 | 기본값 | 효과 |
|---|---|---|
| `trusta.security.trust-gateway-headers` | `false` | `true` 일 때만 `LoginFilter` 가 `X-User-*` 헤더로 SecurityContext 주입 |
| `trusta.messaging.outbox.enabled` | `false` | `true` 일 때만 `OutboxEventListener` / `OutboxCallback` / `OutboxRelayScheduler` / `OutboxDltAckHandler` 등록. Kafka + JPA 의존성도 있어야 활성 |
| `trusta.messaging.inbox.enabled` | `false` | `true` 일 때만 `InboxAdvice` (`@IdempotentConsumer` AOP) / `InboxCleanupScheduler` 등록. JPA 의존성도 있어야 활성 |

### 설정 시나리오별 `application.yaml`

도메인 서비스 유형에 따라 필요한 만큼만 켠다. **아무 것도 안 쓰면 common 의존성만 추가하고 아래 블록 전체 생략 가능** (default 가 모두 false).

#### 1) Gateway 뒤 단순 REST 서비스 (인증만 필요)
```yaml
trusta:
  security:
    trust-gateway-headers: true
```

#### 2) 이벤트 발행하는 서비스 (Outbox 만)
```yaml
trusta:
  security:
    trust-gateway-headers: true
  messaging:
    outbox:
      enabled: true
```

#### 3) 이벤트 소비하는 서비스 (Inbox 만)
```yaml
trusta:
  security:
    trust-gateway-headers: true
  messaging:
    inbox:
      enabled: true
```

#### 4) 이벤트 발행 + 소비 모두 (SAGA / 양방향 이벤트)
```yaml
trusta:
  security:
    trust-gateway-headers: true
  messaging:
    outbox:
      enabled: true
    inbox:
      enabled: true
```

#### 5) Gateway / Config Server 등 DB·Kafka 없는 서비스
```yaml
# 별도 trusta 블록 필요 없음. LoginFilter 도 비활성 유지 (기본값).
# Security 필요하면 trust-gateway-headers 만 켜면 됨.
```

#### 6) 명시적 opt-out (빈이 classpath 에 있어도 로드 안 하고 싶을 때)
```yaml
trusta:
  messaging:
    outbox:
      enabled: false   # default 와 동일하지만 명시적으로 끌 때
    inbox:
      enabled: false
```

---

## 자동 등록되는 기능

소비 서비스의 환경 + 명시적 opt-in 에 따라 **조건부로 등록**된다:
- **Outbox 계열** (Kafka 발행, DLT, relay 스케줄러) — `trusta.messaging.outbox.enabled=true` + Kafka/JPA 의존성
- **Inbox 계열** (`@IdempotentConsumer` AOP, cleanup 스케줄러) — `trusta.messaging.inbox.enabled=true` + JPA 의존성
- **Advice / Security 빈** — 소비자가 자체 빈 등록 시 자동 양보 (`@ConditionalOnMissingBean`)
- **LoginFilter** — `trusta.security.trust-gateway-headers=true` 일 때만 실제 인증 로직 활성

| 기능 | 설명 |
|---|---|
| JPA Auditing + QueryDSL | `createdAt`/`updatedAt` 자동 채움, `JPAQueryFactory` 빈, `EntityScan("com.trustamarket")` |
| ObjectMapper + JsonUtil | JavaTimeModule, ISO 8601 |
| 전역 예외 처리 | 모든 예외를 **RFC 9457** `ErrorResponse` 로 변환 |
| 응답 자동 래핑 | 컨트롤러 반환값을 `CommonResponse` / `PagedResponse` 로 래핑 (Page 자동 감지) |
| MDC 트레이싱 | `X-Trace-Id` 자동 생성/전파 (정규식 검증), `@Async` 에도 유지 |
| Feign 헤더 전파 | 서비스 간 호출 시 `Authorization` / `X-User-*` / `X-Trace-Id` 전파 |
| 비동기 쓰레드풀 | `@Async` 용 (core:10, max:50), `trusta-async-*` prefix |
| Outbox / Inbox | Kafka 이벤트 발행/소비, DLT 상태 머신으로 손실 방지 |
| 페이지네이션 | `RestrictedPageableResolver` — 체인 앞에 prepend, `size` 10/30/50 만 허용 |
| Security | `LoginFilter` opt-in(`trust-gateway-headers`), deny-by-default + actuator 예외 |

---

## 공통 응답 포맷

### 성공 응답 — 타입 분리

단일/일반은 `CommonResponse`, 페이지는 `PagedResponse`, 슬라이스는 `SlicedResponse` 를 사용한다. 타입 시그니처만 봐도 응답 유형이 드러나도록 의도적으로 분리.

```java
// 단일 조회 — Advice 가 CommonResponse 로 자동 래핑
@GetMapping("/{id}")
public OrderResponse get(@PathVariable UUID id) {
    return orderService.get(id);
}

// 페이지 조회 — Page<?> 반환을 Advice 가 감지해 PagedResponse 로 자동 래핑 (COUNT 쿼리 포함)
@GetMapping
public Page<OrderResponse> list(Pageable pageable) {
    return orderService.search(pageable);
}

// 슬라이스 조회 — Slice<?> 반환을 Advice 가 감지해 SlicedResponse 로 자동 래핑 (COUNT 쿼리 없음)
@GetMapping("/feed")
public Slice<OrderResponse> feed(Pageable pageable) {
    return orderService.feed(pageable);
}

// 명시적 사용
return CommonResponse.of(200, orderResponse);
return PagedResponse.of(200, orderPage);
return SlicedResponse.of(200, orderSlice);
```

> `Page<T> extends Slice<T>` 이라 Page 반환도 기술적으론 Slice 이지만, `CommonResponseAdvice` 가 Page 를 먼저 체크해 `PagedResponse` 로 래핑한다.

**단일 응답 JSON** — `{ status, data }`:
```json
{
  "status": 200,
  "data": { "id": "550e8400-...", "orderNumber": "ORD-2026-0001" }
}
```

**페이지 응답 JSON** — `{ status, data, pageInfo }` (COUNT 쿼리 포함, 전체 개수/페이지 수 제공):
```json
{
  "status": 200,
  "data": [ { "id": "...", "orderNumber": "ORD-2026-0001" } ],
  "pageInfo": {
    "page": 0, "size": 10, "totalElements": 42, "totalPages": 5,
    "first": true, "last": false
  }
}
```

**슬라이스 응답 JSON** — `{ status, data, sliceInfo }` (COUNT 쿼리 없음):
```json
{
  "status": 200,
  "data": [ { "id": "...", "orderNumber": "ORD-2026-0001" } ],
  "sliceInfo": {
    "page": 0, "size": 10,
    "first": true, "last": false, "hasNext": true
  }
}
```

| 선택 기준 | `Page` / `PagedResponse` | `Slice` / `SlicedResponse` |
|---|---|---|
| 쿼리 비용 | COUNT 추가 발생 | COUNT 없음 (가벼움) |
| 메타 | totalElements, totalPages | hasNext 만 |
| UX | 페이지 번호 표시 (1, 2, 3, 마지막) | 스크롤 / 더보기 |

### 실패 응답 — `ErrorResponse` (RFC 9457 엄격 준수)

**5개 표준 필드만** 사용: `type`, `title`, `status`, `detail`, `instance`.
확장 필드(`errors`, `timestamp` 등) 금지. 복수 validation 에러도 `detail` 한 줄에 요약.

| 필드 | 설명 |
|---|---|
| `type` | 에러 유형 URI. 모르면 RFC 9457 §4.1 권장값 `"about:blank"` |
| `title` | 에러 유형 요약 (예: `"Not Found"`) |
| `status` | HTTP 상태 코드 |
| `detail` | 발생 사례 상세. 민감 정보 금지 |
| `instance` | 에러 발생 URI (디버깅용) |

**단일 비즈니스 에러:**
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "주문을 찾을 수 없습니다: b5a2b965-...",
  "instance": "/api/orders/b5a2b965-..."
}
```

**복수 필드 validation:**
```json
{
  "type": "about:blank",
  "title": "Validation Error",
  "status": 400,
  "detail": "email: 이메일을 입력해주세요.; password: 비밀번호는 8자 이상...",
  "instance": "/api/users/signup"
}
```

**도메인 type URI 사용 (선택):**
```json
{
  "type": "https://trustamarket.com/errors/order-already-paid",
  "title": "Conflict",
  "status": 409,
  "detail": "이미 결제 완료된 주문입니다.",
  "instance": "/api/orders/1234/pay"
}
```

---

## 공통 예외

| 클래스 | HTTP | 기본 메시지 |
|---|---|---|
| `BadRequestException` | 400 | (메시지 필수) |
| `UnauthorizedException` | 401 | "로그인이 필요한 서비스입니다." |
| `ForbiddenException` | 403 | "해당 작업에 대한 접근 권한이 없습니다." |
| `NotFoundException` | 404 | (메시지 필수) |
| `ConflictException` | 409 | (메시지 필수) |
| `InternalServerException` | 500 | "서버 내부 오류가 발생했습니다." |

### 사용

```java
public OrderResponse get(UUID id) {
    return orderRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("주문을 찾을 수 없습니다: " + id));
}

if (!isValidEmail(email)) {
    throw new BadRequestException("이메일 형식이 올바르지 않습니다.", "email");
}

throw new UnauthorizedException();
throw new ForbiddenException();
```

> **도메인별 `ErrorCode` enum + 단일 Exception 정책은 팀 재량.**
> 간단한 서비스는 위 6개 + 메시지로 충분.
> 복잡한 서비스는 자체 `ErrorCodeSpec` enum 정의 후 `CustomException(errorCode)` 생성자 사용 가능.

### 방어적 생성자 — 서브클래스 status 일치 강제

6개 서브 예외의 `ErrorCodeSpec` 생성자는 **자기 HTTP status 를 강제 검증**한다.
`ConflictException(OrderErrorCode.ORDER_NOT_FOUND)` 처럼 클래스명(409)과 실제 status(404) 가 어긋나면
생성 시점에 `IllegalArgumentException` — 무음 불일치 차단.

내부 구현: `CustomException(ErrorCodeSpec, HttpStatus expected)` 부모 생성자 한 곳에 체크 로직, 각 서브클래스는 `super(errorCode, HttpStatus.XXX)` 1줄씩만 호출.

### GlobalExceptionAdvice — 자동 처리 대상

| 예외 | HTTP | 처리 |
|---|---|---|
| `CustomException` | 예외 `status` | `type`(있으면) + `detail` 에 `"field: message"` |
| `MethodArgumentNotValidException` | 400 | 복수 필드 에러를 `detail` 에 `"field: message; ..."` 요약 |
| `ConstraintViolationException` | 400 | 동일 |
| `HttpMessageNotReadableException` | 400 | "요청 본문을 해석할 수 없습니다." |
| `IllegalArgumentException` | 400 | `detail = e.getMessage()` |
| `ObjectOptimisticLockingFailureException` | 409 | "동시 수정 충돌이 발생했습니다." |
| `Exception` (기타) | 500 | "서버 내부 오류가 발생했습니다." |

---

## 공통 엔티티

### `BaseEntity` — 기본 감사 필드

```java
@Entity
@Table(name = "P_PRODUCT")
public class Product extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private int price;
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `createdAt` | `LocalDateTime` | 생성 시각 (자동, 수정 불가) |
| `updatedAt` | `LocalDateTime` | 수정 시각 (자동) |
| `deletedAt` | `LocalDateTime` | 삭제 시각 (null 이면 미삭제) |

```java
product.delete();     // 멱등 — 이미 deletedAt 있으면 덮어쓰지 않음 (최초 삭제 시각 보존)
product.isDeleted();
```

### `BaseUserEntity` — 유저 감사 확장

```java
@Entity
@Table(name = "P_ORDER")
public class Order extends BaseUserEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `createdBy` | `UUID` | 생성 유저 (자동, 수정 불가) |
| `updatedBy` | `UUID` | 수정 유저 (자동) |
| `deletedBy` | `UUID` | 삭제 유저 |

```java
order.delete(currentUserId);  // 멱등 — 이미 삭제된 엔티티면 no-op (최초 삭제자/시각 보존). null userId 는 IllegalArgumentException
// order.delete();            // ← BaseUserEntity 에선 호출 불가 (UnsupportedOperationException 으로 차단, 반드시 delete(UUID))
```

> Trusta 컨벤션 상 **`domain` 레이어에는 Spring/JPA 의존이 없어야 함.**
> 실무에선 `domain/entity/Order` (순수 자바) + `infrastructure/persistence/jpa/OrderJpaEntity` (JPA) 분리를 권장.
> 단순 서비스라면 `BaseUserEntity` 를 JPA 영속 엔티티에 그대로 사용해도 무방.

---

## Outbox 패턴 — Kafka 이벤트 발행

> ⚠️ **opt-in** — 기본 비활성. `application.yaml` 에 `trusta.messaging.outbox.enabled: true` 명시 + Kafka/JPA 의존성 필요.

트랜잭션 커밋 후 Kafka 로 이벤트를 안전하게 발행. DLT 격리까지 **DB 상태 머신**으로 추적해 메시지 손실 방지.

### 상태 머신

```text
PENDING
  ├─ 성공 ──────→ PROCESSED ✅ (최종)
  └─ 실패 ──────→ FAILED (retryCount++)
                   ├─ retry < 3 ──→ relay 원 토픽 재시도 → PENDING/FAILED 루프
                   └─ retry ≥ 3 ──→ markDltPending (DB 커밋) → sendToDlt
                                      ├─ ack 성공 → DLT_SENT ✅ (최종)
                                      └─ ack 실패 → DLT_PENDING 유지 → relay 재시도
```

핵심 포인트:
- **DLT 격리 결정이 DB 커밋 먼저** — Kafka ack 여부와 무관하게 상태가 남음
- **`sendToDlt` 는 `TransactionSynchronization.afterCommit` 에서 실행** — `DLT_PENDING` 커밋 이후에만 async send 가 시작되어 race 원천 차단
- **async 콜백은 self-invocation 회피 위해 별도 빈** (`OutboxDltAckHandler` with `@Transactional(REQUIRES_NEW)`) 사용
- **relay 스케줄러가 DLT_PENDING 도 재시도** — ack 될 때까지 반복. send 와 상태 전이 try 분리로 중복 발행 방지

### 발행 흐름

```text
서비스 로직 (트랜잭션 내)
  → Events.trigger(OutboxEvent)
  → OutboxEventListener.recordOutbox: Outbox PENDING 저장 (unique 제약 catch)
  → 트랜잭션 커밋
  → OutboxEventListener.publish: Kafka 발행 (AFTER_COMMIT, domainId 파티션 키, null 가드)
  → OutboxCallback.onSuccess: PROCESSED 전이 (REQUIRES_NEW)
  → OutboxCallback.onFailure: FAILED + retryCount++
      └─ retryCount ≥ 3 이면 markDltPending + saveAndFlush 후 sendToDlt
         └─ whenComplete: 성공 시 OutboxDltAckHandler.markDltSent → DLT_SENT

OutboxRelayScheduler (10초 간격)
  ├─ PENDING/FAILED (retryCount < 3)  → 원 토픽 재발행
  └─ DLT_PENDING                       → DLT 토픽 재발행 → ack 시 DLT_SENT
```

### DLT (Dead Letter Topic)

Kafka 발행 **3회 실패** 시 원 토픽 + `.DLT` 토픽으로 격리.
- 예: `order.created` → 3회 실패 → `order.created.DLT`
- DLT_SENT 상태는 **수동 확인 후 재처리** 필요
- DLT 전송 실패해도 DLT_PENDING 으로 DB 에 남아 relay 가 반복 재시도 → **메시지 손실 X**

### `Outbox` 엔티티 (`P_OUTBOX`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `UUID` | 메시지 고유 ID |
| `correlationId` | `String` | SAGA 상관 ID (unique) |
| `domainType` | `String` | 도메인 종류 (`"ORDER"`, `"PRODUCT"`) |
| `domainId` | `UUID` | 도메인 엔티티 ID (파티션 키, nullable) |
| `eventType` | `String` | Kafka 토픽명 |
| `payload` | `TEXT` | JSON 이벤트 데이터 |
| `status` | `Enum` | `PENDING` / `PROCESSED` / `FAILED` / `DLT_PENDING` / `DLT_SENT` |
| `retryCount` | `int` | 재시도 횟수 (3 이상이면 DLT 경로) |

### 사용

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;

    @Transactional
    public OrderResponse createOrder(CreateOrderCommand cmd) {
        Order order = Order.of(cmd);
        orderRepository.save(order);

        Events.trigger(OutboxEvent.of(
            "ORDER",
            order.getId(),
            "order.created",
            new OrderCreatedEvent(order)
        ));

        return OrderResponse.from(order);
    }
}
```

**SAGA (correlationId 이어받기):**
```java
Events.trigger(OutboxEvent.withCorrelation(
    sagaId, "ORDER", order.getId(), "order.created", dto
));
```

---

## Inbox 패턴 — Kafka 멱등성 소비

> ⚠️ **opt-in** — 기본 비활성. `application.yaml` 에 `trusta.messaging.inbox.enabled: true` 명시 + JPA 의존성 필요.

Kafka 메시지 중복 소비 방지. **`(messageId, messageGroup)` 복합 PK** 로 **컨슈머 그룹별 독립 멱등성** 보장.
같은 `message_id` 라도 다른 컨슈머 그룹이면 각 그룹이 한 번씩 독립 처리된다.

### `Inbox` 엔티티 (`P_INBOX`)

복합 PK (`@IdClass(InboxId.class)`):

| 필드 | 타입 | 설명 |
|---|---|---|
| `messageId` (@Id) | `UUID` | Kafka 헤더의 `message_id` 값 |
| `messageGroup` (@Id) | `String` | `@IdempotentConsumer.value()` 로 지정하는 컨슈머 그룹 식별자 |
| `processedAt` | `LocalDateTime` | 처리 시각 (JPA Auditing) |

### 사용

```java
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {
    private final ProductService productService;
    private final JsonUtil jsonUtil;

    @KafkaListener(topics = "order.created", groupId = "product-service")
    @IdempotentConsumer("product-order-consumer")
    public void handleOrderCreated(ConsumerRecord<String, String> record) {
        OrderCreatedEvent event = jsonUtil.fromJson(record.value(), OrderCreatedEvent.class);
        productService.decreaseStock(event.productId(), event.quantity());
    }
}
```

`InboxCleanupScheduler` — 매일 03:00 에 7일 지난 레코드 자동 삭제.

---

## MDC 트레이싱

모든 HTTP 요청에 `X-Trace-Id` 부여 → MSA 전체에서 요청 흐름 추적.

```text
Client → Gateway → 서비스A (X-Trace-Id: abc-123) → 서비스B (X-Trace-Id: abc-123)
```

- 헤더에 `X-Trace-Id` 있고 **정규식 `^[A-Za-z0-9._-]{1,128}$`** 통과 → 그대로 사용
- 비었거나 형식 부적합 → 새 UUID 생성 (log forging / 헤더 인젝션 방지)
- MDC 에 `traceId`/`uri`/`method` 주입 → 로그 자동 포함
- 응답 헤더에 `X-Trace-Id` 추가
- `MdcTaskDecorator` — `@Async` 실행 시에도 `traceId` 유지

**logback 패턴:**
```xml
<pattern>%d{HH:mm:ss} [%X{traceId}] [%thread] %-5level %logger{36} - %msg%n</pattern>
```
```text
10:30:45 [abc-123] [http-nio-8080-exec-1] INFO  c.t.order.OrderService - 주문 생성 완료
10:30:45 [abc-123] [trusta-async-1]       INFO  c.t.order.OrderEventPublisher - Kafka 발행 완료
```

---

## Feign 헤더 전파

| 전파 헤더 | 용도 |
|---|---|
| `Authorization` | 인증 토큰 |
| `X-User-UUID` | 유저 ID |
| `X-User-Email` | 이메일 |
| `X-User-Name` | 이름 |
| `X-User-Role` | 권한 |
| `X-User-Slack-Id` | 슬랙 ID |
| `X-User-Enabled` | 활성화 여부 |
| `X-Trace-Id` | 요청 추적 |

```java
@FeignClient(name = "product-service")
public interface ProductClient {
    @GetMapping("/api/products/{id}")
    CommonResponse<ProductResponse> getProduct(@PathVariable UUID id);
    // Authorization, X-User-*, X-Trace-Id 등이 자동 전파됨
}
```

---

## 페이지네이션 — `RestrictedPageableResolver`

페이지 크기를 **10 / 30 / 50** 으로 제한. 그 외 값은 자동으로 **10** 고정.

| 요청 | 적용 `size` |
|---|---|
| `?size=10` | 10 |
| `?size=30` | 30 |
| `?size=50` | 50 |
| `?size=20` / `?size=100` | 10 |
| size 미지정 | 10 |

---

## Security

### 기본 정책 (`SecurityConfig`)

**deny by default** — `anyRequest().authenticated()` 가 기본값. 공유 모듈이 모든 소비 서비스에 안전한 기본값을 깔아둔다.

예외로 permitAll:
- `/actuator/health`
- `/actuator/info`

소비 서비스에 **public 엔드포인트**가 필요하면 자체 `SecurityFilterChain` 을 `@Bean`으로 등록해 override.

### `LoginFilter` — Gateway 헤더 기반 인증

⚠️ **명시적 opt-in 필요**. default 는 비활성(fail-safe) — header spoofing 방지.

```yaml
trusta:
  security:
    trust-gateway-headers: true   # Gateway 뒤에서만 true. 설정 안 하면 LoginFilter no-op
```

- **`true`**: Gateway 가 strip + re-issue 해준 `X-User-*` 헤더 → `SecurityContext`. 부팅 시 WARN 로그로 trust boundary 상기.
- **`false` (default)**: LoginFilter 가 필터체인 통과만 시키고 `doLogin` 전체 스킵. SecurityConfig `authenticated()` 에 걸려 401. (fail-safe)

새 인증 세팅 직전에만 `SecurityContextHolder.clearContext()` 호출 — 다른 인증 필터(e.g. 내부 서비스 토큰)와 공존 가능.

Gateway 가 넣는 헤더:

| 헤더 | 용도 |
|---|---|
| `X-User-UUID` | 유저 ID (UUID) |
| `X-User-Email` | 이메일 |
| `X-User-Name` | 이름 (URL 인코딩) |
| `X-User-Role` | 권한 (`ROLE_ADMIN` 등) |
| `X-User-Slack-Id` | 슬랙 ID |
| `X-User-Enabled` | 활성화 여부 |

- `X-User-Enabled` 누락 → `BadCredentialsException` (401)
- `enabled=false` → `DisabledException` (401)
- UUID 형식 오류 → `BadCredentialsException` (401)
- UUID/Email 헤더 없으면 익명 요청으로 통과

### 권한 체크

```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping
public OrderResponse create(...) { ... }

@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
@GetMapping
public Page<OrderResponse> list(...) { ... }
```

### `SecurityUtil`

```java
UUID userId = SecurityUtil.getCurrentUserIdOrThrow();    // 없으면 401
Optional<UUID> userId = SecurityUtil.getCurrentUserId();
Optional<String> email = SecurityUtil.getCurrentUsername();
UserDetailsImpl user = SecurityUtil.getCurrentUser().orElseThrow(...);
```

> Role 이름(`ROLE_ADMIN`, `ROLE_USER` 등)은 Trusta 정책 확정 후 팀 합의로 고정한다.

---

## 유틸리티

### `JsonUtil`

```java
@RequiredArgsConstructor
@Service
public class SomeService {
    private final JsonUtil jsonUtil;

    public void example() {
        String json = jsonUtil.toJson(dto);
        ProductResponse dto = jsonUtil.fromJson(json, ProductResponse.class);
        List<ProductResponse> list = jsonUtil.fromJson(json, new TypeReference<>() {});
    }
}
```

---

### 이 라이브러리가 따르는 주요 규칙

- **응답 성공**: `CommonResponse { status, data }` / `PagedResponse { status, data, pageInfo }`
- **응답 실패**: RFC 9457 엄격 (5 필드)
- **VO/DTO/Event**: Java `record` 권장
- **Exception**: 범용 6개 + 팀 재량 `ErrorCodeSpec` enum 확장
- **서비스 패키지**: `com.trustamarket.{서비스}.{도메인}`

---

## TODO

### 인프라
- [x] `trusta-market/common` GitHub 레포 생성
- [ ] 최초 `./gradlew publish` 검증
- [ ] 첫 도메인 서비스 Hello World 확인
- [ ] GitHub Packages 배포 자동화 (CI workflow)

### 고도화
- [ ] `BaseUserEntity` `createdBy` / `updatedBy` JPA Auditing 자동 채움 (SecurityUtil ↔ AuditorAware)
- [x] `@ConditionalOnMissingBean` / `@ConditionalOnBean` 적용 (advice, Outbox/Inbox 빈)
- [ ] Resilience4J (서킷브레이커, 재시도, 벌크헤드) 도입 검토
- [ ] Redis 캐시 추상화
- [ ] Liquibase 도입 검토 (ddl-auto → Liquibase)
- [ ] Security Role 이름 Trusta 정책 확정 후 재검토
- [ ] 인증 정책 확정 후 LoginFilter `trust-gateway-headers` 기본값 재검토 (HMAC 서명 방식 등)

### 완료 (v0.0.1-SNAPSHOT, CodeRabbit 리뷰 2라운드 반영)
- [x] `Inbox` 복합 PK `(messageId, messageGroup)` — 컨슈머 그룹별 독립 멱등성
- [x] `WebConfig` RestrictedPageableResolver 체인 앞 등록 (size 제약 실제 적용)
- [x] `LoginFilter` 조건부 clearContext + `trust-gateway-headers` opt-in 가드
- [x] `SecurityConfig` deny-by-default (+ actuator health/info 예외)
- [x] `CommonAutoConfiguration` 조건부 빈 등록 (`@ConditionalOnBean`/`@ConditionalOnMissingBean`)
- [x] 6개 범용 예외 서브클래스에 status 일치 방어 생성자
- [x] `MdcLoggingFilter` X-Trace-Id 정규식 검증 (log forging 방지)
- [x] Outbox DLT 상태 머신 정식 도입 (`DLT_PENDING` / `DLT_SENT`) — DLT 손실 방지
- [x] `OutboxRelayScheduler` → `OutboxCallback` 위임 + 엔트리별 `REQUIRES_NEW` 트랜잭션
- [x] `OutboxEventListener.publish` domainId null 가드
- [x] Outbox 중복 삽입 TOCTOU → unique 제약 catch
- [x] `BaseEntity` / `BaseUserEntity` 소프트 삭제 멱등성
- [x] `Events.trigger` publisher 미초기화 시 warn 로그
- [x] `InboxCleanupScheduler` cron `zone="UTC"` 고정
- [x] `CommonResponse` `@JsonInclude(NON_NULL)` / `InboxRepository` `clearAutomatically=true`
- [x] DLT `sendToDlt` 를 `TransactionSynchronization.afterCommit` 에 지연 실행 (DLT_PENDING race 차단)
- [x] `BaseUserEntity.delete(userId)` null 가드 + `delete()` 오버라이드 차단
- [x] `OutboxRelayScheduler` send / state-update try 분리 (중복 발행 방지)
- [x] Outbox 빈 `@ConditionalOnBean({KafkaTemplate.class, OutboxRepository.class})` 강화
- [x] `UnAuthorizedException` → `UnauthorizedException` 개명
- [x] `OutboxDltAckHandler` 미조회 warn 로그
- [x] README 코드블록 언어 태그 (MD040)
