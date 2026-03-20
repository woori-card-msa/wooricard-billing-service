package com.card.payment.billing.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/**
 * 배치 스케줄러
 * - 매월 1일 오전 2시 자동 실행 (전월 청구서 생성)
 * - runBillingJob()은 BatchController에서도 사용 (수동 실행)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job billingJob;

    // 매월 1일 오전 2시 실행
    @Scheduled(cron = "0 0 2 1 * *")
    public void runMonthlyBilling() throws Exception {
        String lastMonth = YearMonth.now().minusMonths(1).toString(); // 예: "2026-02"
        log.info("월별 청구 배치 자동 실행 - 대상 월: {}", lastMonth);
        runBillingJob(lastMonth);
    }

    public JobExecution runBillingJob(String billingMonth) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("billingMonth", billingMonth)
                .addLong("timestamp", System.currentTimeMillis()) // 동일 파라미터 중복 실행 방지
                .toJobParameters();

        log.info("배치 Job 실행 시작 - billingMonth: {}", billingMonth);
        JobExecution execution = jobLauncher.run(billingJob, params);
        log.info("배치 Job 실행 완료 - 상태: {}", execution.getStatus());
        return execution;
    }
}
