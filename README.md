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

```
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
│   ├── ErrorResponse.java                    # record. RFC 9457 엄격 (type/title/status/detail/instance)
│   └── CommonResponseAdvice.java             # 컨트롤러 반환값 자동 래핑. Page<?> 자동 감지
│
├── exception/                                # 전역 예외 계층
│   ├── CustomException.java                  # 공통 베이스. type/status/field 필드
│   ├── ErrorCodeSpec.java                    # 도메인별 ErrorCode enum 확장 인터페이스
│   ├── BadRequestException.java              # 400
│   ├── UnAuthorizedException.java            # 401
│   ├── ForbiddenException.java               # 403
│   ├── NotFoundException.java                # 404
│   ├── ConflictException.java                # 409
│   ├── InternalServerException.java          # 500
│   └── GlobalExceptionAdvice.java            # 모든 예외 → ErrorResponse (RFC 9457)
│
├── domain/                                   # JPA 공통 엔티티
│   ├── BaseEntity.java                       # createdAt / updatedAt / deletedAt + soft delete
│   ├── BaseUserEntity.java                   # + createdBy / updatedBy / deletedBy (UUID)
│   ├── outbox/
│   │   ├── Outbox.java                       # P_OUTBOX 엔티티
│   │   ├── OutboxStatus.java                 # PENDING / PROCESSED / FAILED
│   │   └── OutboxRepository.java             # 재시도 대상 조회 + correlationId 조회
│   └── inbox/
│       ├── Inbox.java                        # P_INBOX 엔티티. message_id 기반 중복 방지
│       └── InboxRepository.java              # 오래된 레코드 일괄 삭제 쿼리
│
├── event/                                    # Outbox 이벤트 발행
│   ├── OutboxEvent.java                      # record 이벤트 VO
│   ├── Events.java                           # 정적 trigger() — 주입 없이 발행
│   ├── OutboxEventListener.java              # DB 저장 → Kafka 발행 (AFTER_COMMIT)
│   ├── OutboxCallback.java                   # 성공/실패 콜백 (REQUIRES_NEW) + DLT
│   └── OutboxRelayScheduler.java             # 미발행 메시지 10초 주기 재시도
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

```
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
```

---

## 자동 등록되는 기능

| 기능 | 설명 |
|---|---|
| JPA Auditing + QueryDSL | `createdAt`/`updatedAt` 자동 채움, `JPAQueryFactory` 빈, `EntityScan("com.trustamarket")` |
| ObjectMapper + JsonUtil | JavaTimeModule, ISO 8601 |
| 전역 예외 처리 | 모든 예외를 **RFC 9457** `ErrorResponse` 로 변환 |
| 응답 자동 래핑 | 컨트롤러 반환값을 `CommonResponse` / `PagedResponse` 로 래핑 (Page 자동 감지) |
| MDC 트레이싱 | `X-Trace-Id` 자동 생성/전파, `@Async` 에도 유지 |
| Feign 헤더 전파 | 서비스 간 호출 시 `Authorization` / `X-User-*` / `X-Trace-Id` 전파 |
| 비동기 쓰레드풀 | `@Async` 용 (core:10, max:50), `trusta-async-*` prefix |
| Outbox / Inbox | Kafka 이벤트 발행/소비, 3회 실패 시 DLT 격리 |
| 페이지네이션 | `RestrictedPageableResolver` — `size` 10/30/50 만 허용 |
| Security | `LoginFilter` (헤더 → `SecurityContext`), stateless, 401/403 핸들러 |

---

## 공통 응답 포맷

### 성공 응답 — 타입 분리

단일/일반 응답은 `CommonResponse`, 페이지 응답은 `PagedResponse` 를 사용한다. 타입 시그니처만 봐도 응답 유형이 드러나도록 의도적으로 분리.

```java
// 단일 조회 — Advice 가 CommonResponse 로 자동 래핑
@GetMapping("/{id}")
public OrderResponse get(@PathVariable UUID id) {
    return orderService.get(id);
}

// 페이지 조회 — Page<?> 반환을 Advice 가 감지해 PagedResponse 로 자동 래핑
@GetMapping
public Page<OrderResponse> list(Pageable pageable) {
    return orderService.search(pageable);
}

// 명시적 사용
return CommonResponse.of(200, orderResponse);
return PagedResponse.of(200, orderPage);
```

**단일 응답 JSON** — `{ status, data }`:
```json
{
  "status": 200,
  "data": { "id": "550e8400-...", "orderNumber": "ORD-2026-0001" }
}
```

**페이지 응답 JSON** — `{ status, data, pageInfo }`:
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
| `UnAuthorizedException` | 401 | "로그인이 필요한 서비스입니다." |
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

throw new UnAuthorizedException();
throw new ForbiddenException();
```

> **도메인별 `ErrorCode` enum + 단일 Exception 정책은 팀 재량.**
> 간단한 서비스는 위 6개 + 메시지로 충분.
> 복잡한 서비스는 자체 `ErrorCodeSpec` enum 정의 후 `CustomException(errorCode)` 생성자 사용 가능.

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
product.delete();
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
order.delete(currentUserId);
```

> Trusta 컨벤션 상 **`domain` 레이어에는 Spring/JPA 의존이 없어야 함.**
> 실무에선 `domain/entity/Order` (순수 자바) + `infrastructure/persistence/jpa/OrderJpaEntity` (JPA) 분리를 권장.
> 단순 서비스라면 `BaseUserEntity` 를 JPA 영속 엔티티에 그대로 사용해도 무방.

---

## Outbox 패턴 — Kafka 이벤트 발행

트랜잭션 커밋 후 Kafka 로 이벤트를 안전하게 발행한다.

### 흐름

```
서비스 로직 (트랜잭션 내)
  → Events.trigger(OutboxEvent)
  → OutboxEventListener: Outbox 테이블에 PENDING 저장 (@Transactional REQUIRED)
  → 트랜잭션 커밋
  → OutboxEventListener: Kafka 발행 (AFTER_COMMIT, domainId 가 파티션 키)
  → OutboxCallback.onSuccess(): PROCESSED 전이 (REQUIRES_NEW)
  → OutboxCallback.onFailure(): FAILED + retryCount++, saveAndFlush
  → retryCount ≥ 3: {topic}.DLT 로 격리

OutboxRelayScheduler (10초 간격)
  → PENDING/FAILED (retryCount < 3) 메시지 재발행
```

### DLT (Dead Letter Topic)

Kafka 발행이 **3회 연속 실패**하면 원 토픽 + `.DLT` 토픽으로 메시지 격리.
- 예: `order.created` → 3회 실패 → `order.created.DLT`
- DLT 에 들어간 메시지는 **수동 확인 후 재처리** 필요

### `Outbox` 엔티티 (`P_OUTBOX`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `UUID` | 메시지 고유 ID |
| `correlationId` | `String` | SAGA 상관 ID (unique) |
| `domainType` | `String` | 도메인 종류 (`"ORDER"`, `"PRODUCT"`) |
| `domainId` | `UUID` | 도메인 엔티티 ID |
| `eventType` | `String` | Kafka 토픽명 |
| `payload` | `TEXT` | JSON 이벤트 데이터 |
| `status` | `Enum` | `PENDING` / `PROCESSED` / `FAILED` |
| `retryCount` | `int` | 재시도 횟수 (최대 3) |

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

Kafka 메시지 중복 소비 방지. `message_id` 헤더를 unique 키로 사용.

### `Inbox` 엔티티 (`P_INBOX`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `UUID` | `message_id` (Kafka 헤더) |
| `messageGroup` | `String` | 컨슈머 그룹 식별자 |
| `processedAt` | `LocalDateTime` | 처리 시각 |

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

```
Client → Gateway → 서비스A (X-Trace-Id: abc-123) → 서비스B (X-Trace-Id: abc-123)
```

- 헤더에 `X-Trace-Id` 있으면 그대로, 없으면 UUID 생성
- MDC 에 `traceId`/`uri`/`method` 주입 → 로그 자동 포함
- 응답 헤더에 `X-Trace-Id` 추가
- `MdcTaskDecorator` — `@Async` 실행 시에도 `traceId` 유지

**logback 패턴:**
```xml
<pattern>%d{HH:mm:ss} [%X{traceId}] [%thread] %-5level %logger{36} - %msg%n</pattern>
```
```
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

### `LoginFilter`

Gateway 가 설정한 헤더에서 유저 정보를 추출해 `SecurityContext` 에 주입.

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
- [ ] `trusta-market/common` GitHub 레포 생성
- [ ] 최초 `./gradlew publish` 검증
- [ ] 첫 도메인 서비스 Hello World 확인
- [ ] GitHub Packages 배포 자동화 (CI workflow)

### 고도화
- [ ] `BaseUserEntity` `createdBy` / `updatedBy` JPA Auditing 자동 채움 (SecurityUtil ↔ AuditorAware)
- [ ] `@ConditionalOnMissingBean` 적용 확대 (도메인 서비스가 재정의 가능하도록)
- [ ] Resilience4J (서킷브레이커, 재시도, 벌크헤드) 도입 검토
- [ ] Redis 캐시 추상화
- [ ] Liquibase 도입 검토 (ddl-auto → Liquibase)
- [ ] Security Role 이름 Trusta 정책 확정 후 재검토
