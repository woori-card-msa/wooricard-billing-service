package com.card.payment.billing.batch;

import com.card.payment.billing.entity.Billing;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch Job/Step 설정
 *
 * [흐름]
 * billingJob
 *   └── billingStep (chunk size: 10)
 *        ├── BillingItemReader    : 카드번호 목록 읽기
 *        ├── BillingItemProcessor : 승인내역 조회 → Billing 엔티티 생성
 *        └── BillingItemWriter    : billings 테이블에 저장
 */
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
