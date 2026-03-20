package com.card.payment.billing.batch;

import com.card.payment.billing.client.AuthorizationClient;
import com.card.payment.billing.dto.AuthorizationHistoryResponse;
import com.card.payment.billing.entity.Billing;
import com.card.payment.billing.entity.BillingStatus;
import com.card.payment.billing.repository.BillingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ItemProcessor: 카드번호 → Billing 엔티티 변환
 * - approval-service에서 해당 카드의 월별 승인 내역 조회
 * - 이미 청구된 카드 or 승인 내역 없으면 null 반환 (→ ItemWriter에서 skip)
 * - @StepScope + @Value("#{jobParameters[...]}"): Job 실행 시 billingMonth 파라미터 주입
 */
@Component
@StepScope
@Slf4j
public class BillingItemProcessor implements ItemProcessor<String, Billing> {

    private final AuthorizationClient authorizationClient;
    private final BillingRepository billingRepository;

    @Value("#{jobParameters['billingMonth']}")
    private String billingMonth;

    public BillingItemProcessor(AuthorizationClient authorizationClient,
                                BillingRepository billingRepository) {
        this.authorizationClient = authorizationClient;
        this.billingRepository = billingRepository;
    }

    @Override
    public Billing process(String cardNumberMasked) {
        // 이미 해당 월 청구서가 존재하면 skip
        if (billingRepository.findByCardNumberMaskedAndBillingMonth(cardNumberMasked, billingMonth).isPresent()) {
            log.info("이미 청구 완료된 카드 skip - card: {}, month: {}", cardNumberMasked, billingMonth);
            return null;
        }

        // approval-service에서 승인 내역 조회
        List<AuthorizationHistoryResponse> approvedList;
        try {
            approvedList = authorizationClient.getMonthlyApproved(cardNumberMasked, billingMonth);
        } catch (Exception e) {
            log.error("승인 내역 조회 실패 - card: {}, month: {}", cardNumberMasked, billingMonth, e);
            return null;
        }

        if (approvedList.isEmpty()) {
            log.info("승인 내역 없음 skip - card: {}, month: {}", cardNumberMasked, billingMonth);
            return null;
        }

        // 총 청구금액 합산
        BigDecimal totalAmount = approvedList.stream()
                .map(AuthorizationHistoryResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("청구서 생성 - card: {}, month: {}, amount: {}, 건수: {}",
                cardNumberMasked, billingMonth, totalAmount, approvedList.size());

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
