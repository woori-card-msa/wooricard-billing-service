# Spring Batch 설정 - wooricard-billing-service

## 목차
1. [Spring Batch 핵심 개념](#1-spring-batch-핵심-개념)
2. [전체 처리 흐름](#2-전체-처리-흐름)
3. [변경 파일 목록](#3-변경-파일-목록)
4. [파일별 코드 설명](#4-파일별-코드-설명)
5. [실행 방법](#5-실행-방법)
6. [주요 어노테이션 정리](#6-주요-어노테이션-정리)
7. [Spring Batch 메타 테이블](#7-spring-batch-메타-테이블)
8. [확장 포인트](#8-확장-포인트)

---

## 1. Spring Batch 핵심 개념

Spring Batch는 **대용량 데이터를 일괄 처리**하기 위한 프레임워크다.
핵심 구조는 **Job → Step → Reader/Processor/Writer** 3단계로 이루어진다.

```
Job
 └── Step
      ├── ItemReader    : 처리할 데이터를 읽어온다
      ├── ItemProcessor : 읽은 데이터를 가공/변환한다
      └── ItemWriter    : 가공된 데이터를 저장한다
```

| 용어 | 설명 |
|------|------|
| **Job** | 배치 작업 하나의 단위. 여러 Step으로 구성 가능 |
| **Step** | Job 안의 실행 단계. Reader → Processor → Writer 순서로 동작 |
| **ItemReader** | 데이터 소스(DB, API, 파일 등)에서 데이터를 하나씩 읽어옴. `null` 반환 시 Step 종료 |
| **ItemProcessor** | Reader가 넘긴 데이터를 처리. `null` 반환 시 해당 건 Writer로 넘기지 않고 skip |
| **ItemWriter** | 처리된 데이터를 chunk 단위로 한 번에 저장 |
| **chunk** | 한 번에 처리하는 단위. `chunk(10)` = 10건 읽고 처리한 뒤 한 번에 저장 |
| **JobParameters** | Job 실행 시 외부에서 넘기는 파라미터 (예: billingMonth = "2026-02") |
| **JobRepository** | 배치 실행 이력을 DB에 저장하는 컴포넌트 (Spring이 자동 관리) |

---

## 2. 전체 처리 흐름

### 아키텍처 흐름

```
[클라이언트 or @Scheduled]
        │
        ▼
  BatchController / BatchScheduler
        │  jobLauncher.run(billingJob, params)
        ▼
  ┌─────────────────────────────────────────┐
  │              billingJob                 │
  │                                         │
  │  ┌──────────────────────────────────┐   │
  │  │          billingStep             │   │
  │  │  (chunk size = 10)               │   │
  │  │                                  │   │
  │  │  BillingItemReader               │   │
  │  │   └─ application.yml에서         │   │
  │  │      카드번호 목록 읽기           │   │
  │  │           │                      │   │
  │  │           ▼                      │   │
  │  │  BillingItemProcessor            │   │
  │  │   ├─ 이미 청구된 카드? → skip    │   │
  │  │   ├─ approval-service API 호출   │   │
  │  │   ├─ 승인내역 없으면? → skip     │   │
  │  │   └─ 총액 합산 → Billing 엔티티  │   │
  │  │           │                      │   │
  │  │           ▼ (10건 쌓이면)        │   │
  │  │  BillingItemWriter               │   │
  │  │   └─ billings 테이블에 saveAll   │   │
  │  └──────────────────────────────────┘   │
  └─────────────────────────────────────────┘
        │
        ▼
  JobExecution (COMPLETED / FAILED)
```

### 데이터 흐름 (카드 1장 기준)

```
"1234-****-****-5678"  ← ItemReader가 읽어서 넘김
        │
        ▼
 ItemProcessor
  1. billings 테이블 조회 → 이미 청구? → null 반환 (skip)
  2. approval-service GET /api/authorization/approved/monthly 호출
  3. 승인 내역 없음? → null 반환 (skip)
  4. 승인 금액 합산
  5. Billing 엔티티 생성하여 반환
        │
        ▼
 Billing { cardNumberMasked, billingMonth, totalAmount, ... }
        │
        ▼ (10건 모이면)
 ItemWriter → billingRepository.saveAll(chunk)
        │
        ▼
 billings 테이블에 INSERT
```

---

## 3. 변경 파일 목록

### 신규 생성

```
wooricard-billing-service/
├── src/main/resources/
│   └── application.yml                          ← 신규 생성
│
└── src/main/java/com/card/payment/billing/
    ├── batch/
    │   ├── BillingJobConfig.java                ← 신규 생성
    │   ├── BillingItemReader.java               ← 신규 생성
    │   ├── BillingItemProcessor.java            ← 신규 생성
    │   ├── BillingItemWriter.java               ← 신규 생성
    │   └── BatchScheduler.java                  ← 신규 생성
    │
    └── controller/
        └── BatchController.java                 ← 신규 생성
```

### 기존 파일 수정

```
wooricard-billing-service/
├── build.gradle                                 ← spring-boot-starter-batch 추가
└── src/main/java/.../BillingApplication.java    ← @EnableScheduling 추가
```

---

## 4. 파일별 코드 설명

---

### build.gradle — 의존성 추가

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-batch'  // ← 추가
    ...
}
```

> `spring-boot-starter-batch`를 추가하면 Spring Batch 5.x와 메타 테이블 자동 설정이 포함된다.

---

### application.yml — 설정 파일 (신규)

```yaml
spring:
  application:
    name: billing-service

  datasource:
    url: jdbc:mysql://localhost:3306/billing_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
    username: root
    password: 1234
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQLDialect
    defer-datasource-initialization: true

  sql:
    init:
      mode: always
      encoding: UTF-8

  batch:
    jdbc:
      initialize-schema: always  # Spring Batch 메타 테이블 자동 생성 (MySQL에 9개 테이블)
    job:
      enabled: false             # 서버 시작 시 자동 실행 방지 (중요!)

server:
  port: 8084

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

approval:
  service:
    url: http://localhost:9090

# 배치 처리 대상 카드번호 목록 (현재는 테스트용 고정값)
batch:
  billing:
    card-numbers:
      - "1234-****-****-5678"
      - "9876-****-****-4321"

logging:
  level:
    com.card.payment: DEBUG
```

**핵심 설정 포인트:**

| 설정 | 값 | 이유 |
|------|----|------|
| `spring.batch.jdbc.initialize-schema` | `always` | Spring Batch 메타 테이블을 MySQL에 자동 생성 |
| `spring.batch.job.enabled` | `false` | 서버 재시작 시 배치가 자동 실행되는 것을 막음 |
| `batch.billing.card-numbers` | 카드번호 목록 | ItemReader가 읽을 대상 카드 목록 |

---

### BillingApplication.java — @EnableScheduling 추가

```java
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling          // ← 추가: @Scheduled 어노테이션 활성화
public class BillingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
```

> `@EnableScheduling`이 없으면 `BatchScheduler`의 `@Scheduled`가 동작하지 않는다.

---

### BillingJobConfig.java — Job, Step 정의

```java
@Configuration
@RequiredArgsConstructor
public class BillingJobConfig {

    private final BillingItemReader billingItemReader;
    private final BillingItemProcessor billingItemProcessor;
    private final BillingItemWriter billingItemWriter;

    @Bean
    public Job billingJob(JobRepository jobRepository, Step billingStep) {
        return new JobBuilder("billingJob", jobRepository)
                .start(billingStep)
                .build();
    }

    @Bean
    public Step billingStep(JobRepository jobRepository,
                            PlatformTransactionManager transactionManager) {
        return new StepBuilder("billingStep", jobRepository)
                .<String, Billing>chunk(10, transactionManager)  // 10건씩 처리
                .reader(billingItemReader)
                .processor(billingItemProcessor)
                .writer(billingItemWriter)
                .build();
    }
}
```

**포인트:**
- `JobBuilder("billingJob", ...)` — "billingJob"이 배치 실행 이력에서 사용되는 Job 이름
- `.<String, Billing>chunk(10, ...)` — Reader 입력 타입 `String`, Writer 출력 타입 `Billing`, 10건 단위로 트랜잭션
- Spring Boot 3.x (Batch 5.x)에서는 `@EnableBatchProcessing` 불필요 (자동 설정)

---

### BillingItemReader.java — 카드번호 읽기

```java
@Component
@StepScope   // Step 실행 시마다 새 인스턴스 생성 → index가 매번 0으로 초기화
@Slf4j
public class BillingItemReader implements ItemReader<String> {

    @Value("${batch.billing.card-numbers}")
    private List<String> cardNumbers;   // application.yml에서 주입

    private int index = 0;

    @Override
    public String read() {
        if (index < cardNumbers.size()) {
            String cardNumber = cardNumbers.get(index);
            log.info("카드번호 읽기 [{}/{}] - {}", index + 1, cardNumbers.size(), cardNumber);
            index++;
            return cardNumber;
        }
        return null;  // null 반환 → Spring Batch가 Step 종료 신호로 인식
    }
}
```

**포인트:**
- `read()`가 `null`을 반환하면 Step이 자동으로 종료된다
- `@StepScope` 덕분에 Step이 재실행될 때 `index = 0`으로 초기화된다

---

### BillingItemProcessor.java — 청구서 생성 로직

```java
@Component
@StepScope   // JobParameters 주입을 위해 필수
@Slf4j
public class BillingItemProcessor implements ItemProcessor<String, Billing> {

    private final AuthorizationClient authorizationClient;
    private final BillingRepository billingRepository;

    @Value("#{jobParameters['billingMonth']}")  // Job 실행 시 넘긴 파라미터 주입
    private String billingMonth;

    public BillingItemProcessor(AuthorizationClient authorizationClient,
                                BillingRepository billingRepository) {
        this.authorizationClient = authorizationClient;
        this.billingRepository = billingRepository;
    }

    @Override
    public Billing process(String cardNumberMasked) {
        // 1. 이미 청구된 카드면 skip
        if (billingRepository.findByCardNumberMaskedAndBillingMonth(
                cardNumberMasked, billingMonth).isPresent()) {
            log.info("이미 청구 완료된 카드 skip - card: {}, month: {}", cardNumberMasked, billingMonth);
            return null;  // null 반환 → ItemWriter로 전달되지 않음 (skip)
        }

        // 2. approval-service에서 해당 월 승인 내역 조회
        List<AuthorizationHistoryResponse> approvedList;
        try {
            approvedList = authorizationClient.getMonthlyApproved(cardNumberMasked, billingMonth);
        } catch (Exception e) {
            log.error("승인 내역 조회 실패 - card: {}, month: {}", cardNumberMasked, billingMonth, e);
            return null;  // 조회 실패 시 skip
        }

        // 3. 승인 내역 없으면 skip
        if (approvedList.isEmpty()) {
            log.info("승인 내역 없음 skip - card: {}, month: {}", cardNumberMasked, billingMonth);
            return null;
        }

        // 4. 총 청구금액 합산
        BigDecimal totalAmount = approvedList.stream()
                .map(AuthorizationHistoryResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Billing 엔티티 생성하여 반환 → ItemWriter로 전달
        return Billing.builder()
                .cardNumberMasked(cardNumberMasked)
                .billingMonth(billingMonth)
                .totalAmount(totalAmount)
                .transactionCount(approvedList.size())
                .status(BillingStatus.BILLED)
                .billedAt(LocalDateTime.now())
                .build();
    }
}
```

**포인트:**
- `@Value("#{jobParameters['billingMonth']}")` — SpEL(Spring Expression Language)로 Job 파라미터를 주입
- `@StepScope`가 있어야 JobParameters 주입이 가능하다
- `process()`가 `null`을 반환하면 그 건은 ItemWriter로 넘어가지 않고 자동 skip

---

### BillingItemWriter.java — DB 저장

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class BillingItemWriter implements ItemWriter<Billing> {

    private final BillingRepository billingRepository;

    @Override
    public void write(Chunk<? extends Billing> chunk) {
        billingRepository.saveAll(chunk.getItems());
        log.info("청구서 저장 완료 - {}건", chunk.getItems().size());
    }
}
```

**포인트:**
- Spring Batch 5.x에서는 `write(List<T>)` 대신 `write(Chunk<? extends T>)` 사용
- `chunk.getItems()`로 실제 리스트를 꺼내 `saveAll()`로 한 번에 저장 (성능 최적화)
- chunk 내 하나라도 실패하면 트랜잭션 전체 롤백

---

### BatchScheduler.java — 자동 실행 + Job 실행

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job billingJob;

    // 매월 1일 오전 2시 실행
    @Scheduled(cron = "0 0 2 1 * *")
    public void runMonthlyBilling() throws Exception {
        String lastMonth = YearMonth.now().minusMonths(1).toString();  // 예: "2026-02"
        log.info("월별 청구 배치 자동 실행 - 대상 월: {}", lastMonth);
        runBillingJob(lastMonth);
    }

    // BatchController에서도 이 메서드를 호출해서 수동 실행
    public JobExecution runBillingJob(String billingMonth) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("billingMonth", billingMonth)
                .addLong("timestamp", System.currentTimeMillis())  // 같은 월 재실행 허용
                .toJobParameters();

        log.info("배치 Job 실행 시작 - billingMonth: {}", billingMonth);
        JobExecution execution = jobLauncher.run(billingJob, params);
        log.info("배치 Job 실행 완료 - 상태: {}", execution.getStatus());
        return execution;
    }
}
```

**포인트:**
- cron 표현식: `0 0 2 1 * *` = 초(0) 분(0) 시(2) 일(1) 월(*) 요일(*)
- `timestamp`를 파라미터에 포함하는 이유: Spring Batch는 **동일한 JobParameters로 같은 Job을 두 번 실행 못한다**.
  `timestamp`를 추가하면 매번 다른 파라미터가 되어 같은 월을 여러 번 실행할 수 있다

---

### BatchController.java — REST API 수동 실행

```java
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@Slf4j
public class BatchController {

    private final BatchScheduler batchScheduler;

    @PostMapping("/billing")
    public ResponseEntity<String> triggerBilling(@RequestParam String billingMonth) {
        try {
            log.info("청구 배치 수동 실행 요청 - billingMonth: {}", billingMonth);
            JobExecution execution = batchScheduler.runBillingJob(billingMonth);
            return ResponseEntity.ok("배치 실행 완료 - 상태: " + execution.getStatus());
        } catch (Exception e) {
            log.error("배치 실행 실패 - billingMonth: {}", billingMonth, e);
            return ResponseEntity.internalServerError().body("배치 실행 실패: " + e.getMessage());
        }
    }
}
```

---

## 5. 실행 방법

### 수동 실행 (REST API)

```bash
POST http://localhost:8084/api/batch/billing?billingMonth=2026-02
```

응답 예시:
```
배치 실행 완료 - 상태: COMPLETED
```

### 자동 실행

서버가 실행 중인 상태에서 **매월 1일 오전 2시**에 자동으로 전월 청구서를 생성한다.

### 실행 결과 확인 (MySQL)

```sql
-- 생성된 청구서 확인
SELECT * FROM billings ORDER BY billed_at DESC;

-- Spring Batch 실행 이력 확인
SELECT * FROM BATCH_JOB_EXECUTION ORDER BY START_TIME DESC;

-- Step 실행 이력 확인
SELECT * FROM BATCH_STEP_EXECUTION ORDER BY START_TIME DESC;
```

---

## 6. 주요 어노테이션 정리

| 어노테이션 | 위치 | 역할 |
|-----------|------|------|
| `@EnableScheduling` | `BillingApplication` | `@Scheduled` 어노테이션 활성화 |
| `@StepScope` | Reader, Processor | Step 실행 단위로 Bean 생성 (JobParameters 주입 가능) |
| `@Value("${...}")` | Reader | application.yml 설정값 주입 |
| `@Value("#{jobParameters['...']}") ` | Processor | Job 실행 시 넘긴 파라미터 주입 (SpEL) |
| `@Scheduled(cron = "...")` | BatchScheduler | cron 표현식으로 주기적 실행 |

### @StepScope 를 쓰는 이유

```
@StepScope가 없으면:
  - Bean이 서버 시작 시 딱 한 번 생성됨 (싱글톤)
  - JobParameters 주입 불가 (아직 Job이 실행 전이므로 파라미터가 없음)
  - BillingItemReader의 index가 재사용되어 두 번째 실행 시 아무것도 읽지 못함

@StepScope가 있으면:
  - Step이 실행될 때마다 새 인스턴스 생성
  - JobParameters를 그 시점에 주입받을 수 있음
  - index = 0 으로 항상 초기화됨
```

---

## 7. Spring Batch 메타 테이블

`spring.batch.jdbc.initialize-schema: always` 설정으로 MySQL에 아래 테이블이 자동 생성된다.

| 테이블 | 역할 |
|--------|------|
| `BATCH_JOB_INSTANCE` | Job 인스턴스 목록 (Job 이름 + 파라미터 해시) |
| `BATCH_JOB_EXECUTION` | Job 실행 이력 (STARTED, COMPLETED, FAILED 등) |
| `BATCH_JOB_EXECUTION_PARAMS` | Job 실행 시 사용된 파라미터 |
| `BATCH_STEP_EXECUTION` | Step 실행 이력 (read/write/skip count 포함) |
| `BATCH_STEP_EXECUTION_CONTEXT` | Step 실행 중 상태 저장 (재시작 시 사용) |
| `BATCH_JOB_EXECUTION_CONTEXT` | Job 실행 중 상태 저장 |

---

## 8. 확장 포인트

### ItemReader를 실제 데이터로 교체하기

현재는 `application.yml`에 카드번호를 하드코딩했다.
실제 운영 환경에서는 아래 방식 중 하나로 교체한다.

**방법 A: approval-service API에서 조회**

`AuthorizationClient`에 메서드 추가:
```java
public List<String> getDistinctCardNumbers(String month) {
    return restClient.get()
            .uri("/api/authorization/card-numbers?month=" + month)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
}
```

`BillingItemReader`에서 API 호출로 목록 생성:
```java
@Bean
@StepScope
public ItemReader<String> billingItemReader(
        @Value("#{jobParameters['billingMonth']}") String billingMonth) {
    List<String> cardNumbers = authorizationClient.getDistinctCardNumbers(billingMonth);
    return new ListItemReader<>(cardNumbers);
}
```

**방법 B: 별도 cards 테이블 조회 (JdbcCursorItemReader)**

```java
@Bean
@StepScope
public JdbcCursorItemReader<String> billingItemReader(DataSource dataSource) {
    return new JdbcCursorItemReaderBuilder<String>()
            .name("cardNumberReader")
            .dataSource(dataSource)
            .sql("SELECT card_number_masked FROM cards WHERE active = true")
            .rowMapper((rs, rowNum) -> rs.getString("card_number_masked"))
            .build();
}
```

### 실패한 카드만 재처리하기

`BillingStatus.FAILED` 상태를 추가하고, Reader에서 FAILED 건만 읽도록 설정하면
실패한 카드만 골라서 재배치할 수 있다.
