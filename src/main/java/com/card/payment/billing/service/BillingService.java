package com.card.payment.billing.service;

import com.card.payment.billing.client.AuthorizationClient;
import com.card.payment.billing.dto.AuthorizationHistoryResponse;
import com.card.payment.billing.dto.BillingResponse;
import com.card.payment.billing.entity.Billing;
import com.card.payment.billing.entity.BillingStatus;
import com.card.payment.billing.repository.BillingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final BillingRepository billingRepository;
    private final AuthorizationClient authorizationClient;

    @Transactional
    public BillingResponse createMonthlyBilling(
            String cardNumber, String month) {

        log.info("청구서 생성 시작 - cardNumber: {}, month: {}",
                cardNumber, month);

        // 1. 카드번호 마스킹
        String masked = maskCardNumber(cardNumber);

        // 2. approval-service에서 해당 월 승인 내역 조회
        List<AuthorizationHistoryResponse> approvedList =
                authorizationClient.getMonthlyApproved(masked, month);

        if (approvedList.isEmpty()) {
            throw new IllegalStateException("해당 월 승인 내역이 없습니다");
        }

        // 3. 총 청구금액 합산
        BigDecimal totalAmount = approvedList.stream()
                .map(AuthorizationHistoryResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. billings 테이블 저장
        Billing billing = Billing.builder()
                .cardNumberMasked(masked)
                .billingMonth(month)
                .totalAmount(totalAmount)
                .transactionCount(approvedList.size())
                .status(BillingStatus.BILLED)
                .billedAt(LocalDateTime.now())
                .build();

        billingRepository.save(billing);

        log.info("청구서 생성 완료 - cardNumberMasked: {}, totalAmount: {}",
                masked, totalAmount);

        return BillingResponse.builder()
                .cardNumberMasked(masked)
                .billingMonth(month)
                .totalAmount(totalAmount)
                .transactionCount(approvedList.size())
                .status(BillingStatus.BILLED.name())
                .billedAt(billing.getBilledAt())
                .build();
    }

    public List<BillingResponse> getBillingHistory(String cardNumber) {
        // 마스킹된 값으로 조회
        String masked = maskCardNumber(cardNumber);

        return billingRepository
                .findByCardNumberMaskedOrderByBillingMonthDesc(masked)
                .stream()
                .map(b -> BillingResponse.builder()
                        .cardNumberMasked(b.getCardNumberMasked())
                        .billingMonth(b.getBillingMonth())
                        .totalAmount(b.getTotalAmount())
                        .transactionCount(b.getTransactionCount())
                        .status(b.getStatus().name())
                        .billedAt(b.getBilledAt())
                        .build())
                .toList();
    }

    // 카드번호 마스킹 (1234-****-****-5678)
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 16) {
            return cardNumber;
        }
        return cardNumber.substring(0, 4)
                + "-****-****-"
                + cardNumber.substring(cardNumber.length() - 4);
    }
}