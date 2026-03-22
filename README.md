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
- **Spring Cloud 2025.0.1**
- **Spring Data JPA**
- **OpenFeign** (서비스 간 통신)
- **Eureka Client** (서비스 디스커버리)
- **MySQL 8.0**
- **Lombok**
- **Swagger (SpringDoc OpenAPI 2.3.0)**

---

## 서비스 아키텍처

```
wooricard-approval-service (:8081)
  ↓ OpenFeign
  GET /api/authorization/approved/monthly
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
  → 카드번호 마스킹 처리
  → approval-service에서 해당 월 승인 내역 조회 (OpenFeign)
  → 카드별 사용금액 합산
  → 청구서 생성 (billings 테이블 저장)
```

### 청구 상태 흐름

```
PENDING (청구 대기)
  → BILLED (청구 완료)
    → PAID (납부 완료)
```

### 카드번호 마스킹

```
입력:  6011111111111117
출력:  6011-****-****-1117
```

---

## 프로젝트 구조

```
wooricard-billing-service/
├── build.gradle
├── src/main/
│   ├── java/com/card/payment/billing/
│   │   ├── BillingApplication.java          # @EnableFeignClients
│   │   ├── client/
│   │   │     └── AuthorizationClient.java   # OpenFeign interface
│   │   ├── controller/
│   │   │     └── BillingController.java
│   │   ├── dto/
│   │   │     ├── AuthorizationHistoryResponse.java
│   │   │     └── BillingResponse.java
│   │   ├── entity/
│   │   │     ├── Billing.java
│   │   │     └── BillingStatus.java
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
| cardNumber | String | ✅ | 카드번호 (원본) |
| month | String | ✅ | 청구 년월 (예: 2026-03) |

**요청 예시**
```
POST http://localhost:8083/api/billing/monthly
  ?cardNumber=6011111111111117
  &month=2026-03
```

**응답 예시**
```json
{
  "cardNumberMasked": "6011-****-****-1117",
  "billingMonth": "2026-03",
  "totalAmount": 50000,
  "transactionCount": 1,
  "status": "BILLED",
  "billedAt": "2026-03-20T14:36:48"
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
GET http://localhost:8083/api/billing/6011111111111117
```

**응답 예시**
```json
[
  {
    "cardNumberMasked": "6011-****-****-1117",
    "billingMonth": "2026-03",
    "totalAmount": 50000,
    "transactionCount": 1,
    "status": "BILLED",
    "billedAt": "2026-03-20T14:36:48"
  }
]
```

---

## 서비스 간 통신

### OpenFeign으로 approval-service 호출

```java
@FeignClient(
    name = "wooricard-approval-service"
)
public interface AuthorizationClient {

    @GetMapping("/api/authorization/approved/monthly")
    List<AuthorizationHistoryResponse> getMonthlyApproved(
            @RequestParam String cardNumberMasked,
            @RequestParam String month
    );
}
```

### 통신 흐름

```
POST /api/billing/monthly?cardNumber=6011111111111117&month=2026-03
  ↓
BillingService
  ↓ 카드번호 마스킹 (6011-****-****-1117)
  ↓ OpenFeign 호출
approval-service
  GET /api/authorization/approved/monthly
    ?cardNumberMasked=6011-****-****-1117&month=2026-03
  ↓ 승인 내역 반환
BillingService
  ↓ 금액 합산 → 청구서 생성
billing_db 저장
```

### Eureka 연동

```
Eureka 대시보드: http://192.168.1.80:8761
등록 서비스: WOORICARD-BILLING-SERVICE (8083)
```

---

## 환경 설정

### 환경 설정 (Environment Variables)

이 프로젝트는 환경 변수를 통해 설정을 관리합니다. 
로컬 실행을 위해 프로젝트 루트 디렉토리에 `.env` 파일을 생성하고 필요한 값을 설정해주세요.

1. `.env.example` 파일을 복사하여 `.env` 파일을 생성합니다.
```.env
  # Server Configuration
  SERVER_PORT=8083
  APP_NAME=wooricard-billing-service
  
  # Infrastructure Addresses
  CONFIG_SERVER_URL=http://localhost:8888
  EUREKA_SERVER_URL=http://localhost/eureka/
  
  # Database
  DB_HOST=localhost
  DB_USERNAME=your_username
  DB_PASSWORD=your_password
```

2. 각 항목에 맞는 로컬 인프라 정보를 입력합니다. (DB 계정 등)

| 변수명 | 설명 | 기본값 |
| :--- | :--- | :--- |
| `SERVER_PORT` | 서비스 포트 번호 | `8083` |
| `CONFIG_SERVER_URL` | Config 서버 주소 | `http://localhost:8888` |
| `DB_PASSWORD` | 로컬 MySQL 비밀번호 | (팀 내부 공유 필요) |

### ⚠️ 실행 전 주의사항
본 서비스는 중앙 설정 관리 서버가 필요합니다. 
반드시 아래 서버를 먼저 구동한 후 실행해 주세요.

1. **Eureka Server**: [이동하기](https://github.com/woori-card-msa/wooricard-eureka?tab=readme-ov-file#-%EC%8B%A4%ED%96%89-%EB%B0%A9%EB%B2%95-getting-started)
2. **Config Server**: [이동하기](https://github.com/woori-card-msa/wooricard-config?tab=readme-ov-file#-%EC%8B%A4%ED%96%89-%EB%B0%A9%EB%B2%95-getting-started)
3. **Approval Service** : [이동하기](https://github.com/woori-card-msa/wooricard-approval-service?tab=readme-ov-file#%ED%99%98%EA%B2%BD-%EC%84%A4%EC%A0%95)

> **Tip:** Config Server의 구동 방식이나 Git Repo 설정은 [해당 README.md](https://github.com/woori-card-msa/wooricard-config#readme)를 참고하세요.

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

### approval-service 연결 실패 시

```
1. approval-service 실행 여부 확인
2. application.yml의 approval.service.url 확인
3. 다른 PC라면 IP 주소로 변경
   approval.service.url: http://유림PC_IP:8081
```

### Eureka 연결 실패 시

```
Eureka 서버 실행 여부 확인
http://192.168.1.80:8761 접속 확인
```
