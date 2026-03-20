package com.card.payment.billing.controller;

import com.card.payment.billing.batch.BatchScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치 수동 실행 API
 *
 * POST /api/batch/billing?billingMonth=2026-02
 */
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
