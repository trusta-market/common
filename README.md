# Trusta Common

Trusta MSA 프로젝트의 **공통 라이브러리**. 모든 도메인 서비스에서 의존성으로 추가해 사용한다.

- **패키지 베이스**: `com.trustamarket.common`
- **Artifact**: `com.trustamarket:common:0.0.1-SNAPSHOT`
- **배포**: GitHub Packages (`trusta-market/common`)
- **Java**: 21 / **Spring Boot**: 3.5.13 / **Spring Cloud**: 2025.0.1

> API 응답은 `{ status, data }` 를 기본으로 하며, 페이지 조회는 `pageInfo`, 슬라이스 조회는 `sliceInfo` 를 추가로 포함한다. 에러 응답은 **RFC 9457 Problem Details**를 엄격히 준수한다.

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
10. [이벤트 경량 계약 (Outbox/Inbox 는 도메인 구현)](#이벤트-경량-계약--outboxinbox-는-도메인에서-직접-구현)
11. [MDC 트레이싱 / Feign / Pageable / Security / Util](#mdc-트레이싱)

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.13, Spring Cloud 2025.0.1 |
| 계층 | Spring MVC (Servlet) |
| ORM | JPA + QueryDSL 5.1.0 (Auditing, Soft Delete, EntityScan) |
| Messaging | Kafka 경량 계약 — `OutboxEvent` / `Events.trigger` / `@IdempotentConsumer` (구현체는 도메인) |
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
├── domain/                                   # JPA 공통 엔티티 (Outbox/Inbox 는 도메인에서 직접)
│   ├── BaseCreatedEntity.java                # createdAt 만 — append-only (이력/이벤트/트랜잭션 로그)
│   ├── BaseTimeEntity.java                   # + updatedAt — soft delete 없음 (wallet, config, 상태 관리)
│   ├── BaseEntity.java                       # + deletedAt — 표준 soft delete
│   └── BaseUserEntity.java                   # + createdBy / updatedBy / deletedBy — 유저 감사까지
│
├── event/                                    # 이벤트 경량 계약 (구현체는 도메인)
│   ├── OutboxEvent.java                      # record. 이벤트 envelope — correlationId / domainType / domainId / eventType / payload
│   └── Events.java                           # 정적 trigger() — ApplicationEventPublisher 를 감싼 발행 게이트웨이
│
├── messaging/                                # 멱등 소비 계약
│   └── IdempotentConsumer.java               # @IdempotentConsumer 애노테이션 (마커)
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
```

### Trusta 프로퍼티 요약

| 프로퍼티 | 기본값 | 효과 |
|---|---|---|
| `trusta.security.trust-gateway-headers` | `false` | `true` 일 때만 `LoginFilter` 가 `X-User-*` 헤더로 SecurityContext 주입 |

---

## 자동 등록되는 기능

- **Advice / Security 빈** — 소비자가 자체 빈 등록 시 자동 양보 (`@ConditionalOnMissingBean`)
- **LoginFilter** — `trusta.security.trust-gateway-headers=true` 일 때만 실제 인증 로직 활성
- **이벤트 관련** — `OutboxEvent` record / `Events.trigger` / `@IdempotentConsumer` 만 경량 계약으로 제공. **실제 인프라 구현 (Outbox/Inbox 테이블, 리스너, 스케줄러) 은 각 도메인이 직접 구현** — [아래 섹션](#이벤트-경량-계약--outboxinbox-는-도메인에서-직접-구현) 참조

| 기능 | 설명 |
|---|---|
| JPA Auditing + QueryDSL | `createdAt`/`updatedAt` 자동 채움, `JPAQueryFactory` 빈, `EntityScan("com.trustamarket")` |
| ObjectMapper + JsonUtil | JavaTimeModule, ISO 8601 |
| 전역 예외 처리 | 모든 예외를 **RFC 9457** `ErrorResponse` 로 변환 |
| 응답 자동 래핑 | 컨트롤러 반환값을 `CommonResponse` / `PagedResponse` / `SlicedResponse` 로 래핑. null / 204 / 304 는 통과 |
| MDC 트레이싱 | `X-Trace-Id` 자동 생성/전파 (정규식 검증), `@Async` 에도 유지 |
| Feign 헤더 전파 | 서비스 간 호출 시 `Authorization` / `X-User-*` / `X-Trace-Id` 전파 |
| 비동기 쓰레드풀 | `@Async` 용 (core:10, max:50), `trusta-async-*` prefix |
| 이벤트 경량 계약 | `OutboxEvent` / `Events.trigger` / `@IdempotentConsumer` 만 제공, 인프라 구현은 도메인 |
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

4단계 선형 체인 — 엔티티 라이프사이클에 맞춰 골라 상속:

```text
BaseCreatedEntity   (createdAt)
    ↑
BaseTimeEntity      (+ updatedAt)
    ↑
BaseEntity          (+ deletedAt + soft delete)
    ↑
BaseUserEntity      (+ createdBy / updatedBy / deletedBy)
```

### 선택 가이드

| 베이스 | 대상 | 예시 |
|---|---|---|
| `BaseCreatedEntity` | **append-only** — 생성 후 수정/삭제 불가 | 이력 테이블 (status_history, order_event_log), 트랜잭션 로그 (p_point_transactions), 이벤트 inbox/outbox, tracking |
| `BaseTimeEntity` | 수정은 가능하지만 **soft delete 불필요** — status 로 라이프사이클 관리 | p_wallets (ACTIVE/FROZEN/CLOSED), trust_score, config, delivery, inspection_centers |
| `BaseEntity` | 표준 엔티티 — 생성/수정/소프트 삭제. user 감사 불필요 | (유저 감사 없이도 되는 일반 엔티티) |
| `BaseUserEntity` | + **누가** 만들고 수정하고 삭제했는지 감사 필요 | User, Order, Product, Payment, Refund 등 주요 도메인 엔티티 |

### `BaseCreatedEntity` — append-only

```java
@Entity
@Table(name = "order_event_log")
public class OrderEventLog extends BaseCreatedEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID eventId;
    // createdAt 만 상속. update 하면 안 되는 테이블에 사용
}
```

### `BaseTimeEntity` — soft delete 없는 수정 가능 엔티티

```java
@Entity
@Table(name = "p_wallets")
public class Wallet extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID walletId;
    private WalletStatus status;  // ACTIVE → FROZEN → CLOSED 로 status 로 라이프사이클 관리
}
```

### `BaseEntity` — 표준 soft delete

```java
@Entity
@Table(name = "P_PRODUCT")
public class Product extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
}
```

```java
product.delete();     // 멱등 — 이미 deletedAt 있으면 덮어쓰지 않음 (최초 삭제 시각 보존)
product.isDeleted();
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `createdAt` | `Instant` | 생성 시각 (UTC, 자동, 수정 불가). `BaseCreatedEntity` 에서 상속. PostgreSQL `TIMESTAMPTZ` 와 매핑 |
| `updatedAt` | `Instant` | 수정 시각 (UTC, 자동). `BaseTimeEntity` 에서 상속 |
| `deletedAt` | `Instant` | 삭제 시각 (UTC, null 이면 미삭제) |

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

## 이벤트 경량 계약 — Outbox/Inbox 는 도메인에서 직접 구현

Trusta common 은 Kafka 관련해서 **경량 계약만 제공** 하고, 실제 Outbox/Inbox 테이블/리스너/스케줄러 등 인프라 구현은 각 도메인 서비스가 **자기 스타일로 구현**한다.

### common 이 제공하는 것 (전부)

| 항목 | 종류 | 용도 |
|---|---|---|
| `OutboxEvent` | `record` | 이벤트 envelope 타입 — `correlationId`, `domainType`, `domainId`, `eventType`, `payload` |
| `Events.trigger(OutboxEvent)` | static method | `ApplicationEventPublisher` 를 감싼 발행 게이트웨이. 주입 없이 정적 호출 |
| `@IdempotentConsumer("groupName")` | annotation | Kafka 리스너에 붙이는 마커. 도메인 AOP 에서 해석해 멱등 소비 구현 |

### 도메인 서비스가 직접 구현해야 하는 것

- **Outbox 테이블 + 엔티티 + Repository** — 도메인 스키마에 맞게 정의
- **`OutboxEvent` 리스너** — `@EventListener` + `@Transactional(REQUIRED)` 로 Outbox row 저장
- **Kafka 발행 로직** — `@TransactionalEventListener(AFTER_COMMIT)` 로 Kafka 발행, 상태 전이 (PROCESSED/FAILED), DLT 격리
- **재시도 스케줄러** — 주기적으로 미발행 Outbox 재처리
- **Inbox 테이블 + Repository** — 중복 소비 방지용
- **`@IdempotentConsumer` AOP** — 리스너 메서드 실행 전 Inbox 중복 체크
- **Inbox cleanup 스케줄러** — 오래된 레코드 삭제

### 발행 측 사용 패턴 예시

```java
// 도메인이 직접 구현한 OutboxEventListener (common 에는 없음)
@Component
@RequiredArgsConstructor
public class OrderOutboxEventListener {
    private final OutboxRepository outboxRepository;   // 도메인 자체
    private final KafkaTemplate<String, String> kafkaTemplate;

    @EventListener
    @Transactional(REQUIRED)
    public void recordOutbox(OutboxEvent event) { ... }

    @TransactionalEventListener(AFTER_COMMIT)
    public void publish(OutboxEvent event) { ... }
}

// 도메인 서비스
@Service
@Transactional
public class OrderService {
    public void createOrder(...) {
        orderRepository.save(order);
        Events.trigger(OutboxEvent.of("ORDER", order.getId(), "order.created", payload));
    }
}
```

### 소비 측 사용 패턴 예시

```java
// 도메인이 직접 구현한 InboxAdvice (common 에는 없음)
@Aspect
@Component
@RequiredArgsConstructor
public class MyInboxAdvice {
    private final InboxRepository inboxRepository;   // 도메인 자체

    @Around("@annotation(com.trustamarket.common.messaging.IdempotentConsumer)")
    public Object dedup(ProceedingJoinPoint pjp) { ... }
}

// 도메인 컨슈머
@KafkaListener(topics = "order.created", groupId = "product-service")
@IdempotentConsumer("product-order-consumer")
public void handleOrderCreated(ConsumerRecord<String, String> record) { ... }
```

### 왜 common 에서 인프라 구현을 뺐나

- **강결합 방지** — common 에 `OutboxRepository`/`InboxRepository` JPA 구현이 있으면 모든 도메인이 공통 엔티티 스키마에 묶임
- **도메인 자율성** — retry 주기, DLT 네이밍, 상태 전이 규칙을 도메인별로 자유롭게 선택
- **트레이싱 용이** — 디버깅 시 common 라이브러리까지 들어가지 않아도 자기 도메인 코드만 보면 됨
- **`common-event` 모듈 분리 여지** — 추후 수요 생기면 별도 의존성으로 뽑기 쉬움

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

### 완료 (v0.0.1-SNAPSHOT)
- [x] `WebConfig` RestrictedPageableResolver 체인 앞 등록
- [x] `LoginFilter` 조건부 clearContext + `trust-gateway-headers` opt-in 가드
- [x] `SecurityConfig` deny-by-default (+ actuator health/info 예외)
- [x] `CommonAutoConfiguration` `@ConditionalOnMissingBean` 적용 (Security/Advice 빈)
- [x] 6개 범용 예외 서브클래스에 status 일치 방어 생성자
- [x] `MdcLoggingFilter` X-Trace-Id 정규식 검증 (log forging 방지)
- [x] `BaseEntity` / `BaseUserEntity` 소프트 삭제 멱등성 + null userId 가드
- [x] `Events.trigger` publisher 미초기화 시 warn 로그
- [x] `CommonResponse` `@JsonInclude(NON_NULL)`
- [x] `GlobalExceptionAdvice` `getAllErrors()` 로 class-level validation 포함
- [x] `UnAuthorizedException` → `UnauthorizedException` 개명
- [x] `CommonResponseAdvice` `@RestControllerAdvice` + `ResponseEntity<String>` 필터 + null/204/304 통과
- [x] `SlicedResponse` / `SliceInfo` 추가 + Advice Slice 자동 래핑
- [x] README 코드블록 언어 태그 (MD040)
- [x] **Outbox/Inbox 구현체 common 에서 제거** — `OutboxEvent` record / `Events.trigger` / `@IdempotentConsumer` 만 경량 계약으로 남김. 인프라 구현은 각 도메인
