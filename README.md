# wooricard-billing-service

> Woori Card MSA 프로젝트의 **매입 청구 서비스**입니다.
> 카드 승인 내역을 바탕으로 월별 청구서를 생성하고 관리합니다.

---

## 목차

- [서비스 개요](#서비스-개요)
- [기술 스택](#기술-스택)
- [서비스 아키텍처](#서비스-아키텍처)
- [서비스 역할](#서비스-역할)
- [프로젝트 구조](#프로젝트-구조)
- [DB 설계](#db-설계)
- [API 명세](#api-명세)
- [서비스 간 통신](#서비스-간-통신)
- [실행 방법](#실행-방법)
- [환경 설정](#환경-설정)
- [트러블슈팅](#트러블슈팅)

---

## 서비스 개요

| 항목 | 내용 |
|------|------|
| **서비스명** | wooricard-billing-service |
| **포트** | 8083 |
| **DB** | billing_db |
| **역할** | 매입 청구 서비스 |
| **그룹** | com.wooricard |
| **Spring Boot** | 3.5.12 |
| **Spring Cloud** | 2025.0.1 |

---

## 기술 스택

- **Java 17**
- **Spring Boot 3.5.12**
- **Spring Cloud 2025.0.1** (Eureka Client)
- **Spring Data JPA**
- **MySQL 8.0**
- **Lombok**
- **Swagger (SpringDoc OpenAPI 2.3.0)**

---

## 서비스 아키텍처

```
wooricard-approval-service (:8081)
  ↓ GET /api/authorization/approved/monthly
wooricard-billing-service (:8083)   ← 본 서비스
  ↓ 카드별 사용금액 합산
  ↓ 청구서 생성
billing_db
  └── billings 테이블

wooricard-eureka (:8761)
  ↑ 서비스 등록
wooricard-billing-service
```

---

## 서비스 역할

매입 청구 서비스는 카드 결제 프로세스의 **마지막 단계**를 담당합니다.

```
[월말]
billing-service
  → approval-service에서 해당 월 승인 내역 조회
  → 카드별 사용금액 합산
  → 청구서 생성 (billings 테이블 저장)
```

### 청구 상태 흐름

```
PENDING (청구 대기)
  → BILLED (청구 완료)
    → PAID (납부 완료)
```

---

## 프로젝트 구조

```
wooricard-billing-service/
├── build.gradle
├── src/main/
│   ├── java/com/card/payment/billing/
│   │   ├── BillingApplication.java
│   │   ├── client/
│   │   │     └── AuthorizationClient.java   # approval-service 호출
│   │   ├── controller/
│   │   │     └── BillingController.java
│   │   ├── dto/
│   │   │     ├── AuthorizationHistoryResponse.java
│   │   │     └── BillingResponse.java
│   │   ├── entity/
│   │   │     ├── Billing.java
│   │   │     └── BillingStatus.java         # PENDING / BILLED / PAID
│   │   ├── repository/
│   │   │     └── BillingRepository.java
│   │   └── service/
│   │         └── BillingService.java
│   └── resources/
│         └── application.yml
└── README.md
```

---

## DB 설계

### billings 테이블

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | BIGINT PK | 자동 증가 |
| card_number_masked | VARCHAR(19) | 마스킹된 카드번호 (1234-\*\*\*\*-\*\*\*\*-5678) |
| billing_month | VARCHAR(7) | 청구 년월 (2026-03) |
| total_amount | DECIMAL(15,2) | 총 청구금액 |
| transaction_count | INT | 청구 건수 |
| status | ENUM | PENDING / BILLED / PAID |
| billed_at | DATETIME | 청구 완료 일시 |
| created_at | DATETIME | 생성 일시 |

### DB 생성

```sql
CREATE DATABASE billing_db;
```

---

## API 명세

### Swagger UI

```
http://localhost:8083/swagger-ui.html
```

### 엔드포인트 목록

#### 1. 월별 청구서 생성

```
POST /api/billing/monthly
```

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| cardNumber | String | ✅ | 카드번호 |
| month | String | ✅ | 청구 년월 (예: 2026-03) |

**요청 예시**
```
POST http://localhost:8083/api/billing/monthly
  ?cardNumber=4111111111111111
  &month=2026-03
```

**응답 예시**
```json
{
  "cardNumberMasked": "4111-****-****-1111",
  "billingMonth": "2026-03",
  "totalAmount": 30000,
  "transactionCount": 3,
  "status": "BILLED",
  "billedAt": "2026-03-20T10:00:00"
}
```

#### 2. 청구 내역 조회

```
GET /api/billing/{cardNumber}
```

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| cardNumber | String | 카드번호 |

**요청 예시**
```
GET http://localhost:8083/api/billing/4111111111111111
```

**응답 예시**
```json
[
  {
    "cardNumberMasked": "4111-****-****-1111",
    "billingMonth": "2026-03",
    "totalAmount": 30000,
    "transactionCount": 3,
    "status": "BILLED",
    "billedAt": "2026-03-20T10:00:00"
  }
]
```

#### 3. 헬스체크

```
GET /api/health
```

---

## 서비스 간 통신

### approval-service 호출

billing-service는 **RestClient**를 사용하여 approval-service의 월별 승인 내역을 조회합니다.

```
GET /api/authorization/approved/monthly
  ?cardNumber={cardNumber}&month={month}
```

### Eureka 연동

```
Eureka 대시보드: http://192.168.1.80:8761
등록 서비스: WOORICARD-BILLING-SERVICE (8083)
```

---

## 실행 방법

### 1. DB 생성

```sql
CREATE DATABASE billing_db;
```

### 2. 서비스 기동 순서

```
1. wooricard-eureka           (:8761)  먼저 실행
2. wooricard-approval-service (:8081)  실행
3. wooricard-billing-service  (:8083)  실행
```

### 3. IntelliJ에서 실행

```
BillingApplication.java 열기
→ 좌측 초록색 ▶ 버튼 클릭
```

### 4. 실행 확인

```
Swagger:  http://localhost:8083/swagger-ui.html
Eureka:   http://192.168.1.80:8761
```

---

## 환경 설정

### application.yml

```yaml
spring:
  application:
    name: wooricard-billing-service
  datasource:
    url: jdbc:mysql://localhost:3306/billing_db
  jpa:
    hibernate:
      ddl-auto: create-drop
  sql:
    init:
      mode: never

server:
  port: 8083

# 승인 서비스 연동
approval:
  service:
    url: http://localhost:8081

# Eureka 연동
eureka:
  client:
    service-url:
      defaultZone: http://192.168.1.80:8761/eureka/
  instance:
    prefer-ip-address: true
```

### build.gradle 주요 의존성

```gradle
plugins {
    id 'org.springframework.boot' version '3.5.12'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.wooricard'

ext {
    set('springCloudVersion', "2025.0.1")
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
    runtimeOnly 'com.mysql:mysql-connector-j'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    implementation project(':common')
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}
```

---

## 트러블슈팅

### data.sql 오류 발생 시

```yaml
spring:
  sql:
    init:
      mode: never
```

### Spring Cloud 버전 호환성 오류 발생 시

```yaml
spring:
  cloud:
    compatibility-verifier:
      enabled: false
```

### Eureka 연결 실패 시

```
Eureka 서버가 먼저 실행되어 있는지 확인
http://192.168.1.80:8761 접속 확인
```
