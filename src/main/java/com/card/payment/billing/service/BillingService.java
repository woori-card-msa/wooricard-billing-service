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

    // 월별 청구서 생성
    @Transactional
    public BillingResponse createMonthlyBilling(
            String cardNumber, String month) {

        log.info("청구서 생성 시작 - cardNumber: {}, month: {}",
                cardNumber, month);

        // 1. approval-service에서 해당 월 승인 내역 조회
        List<AuthorizationHistoryResponse> approvedList =
                authorizationClient.getMonthlyApproved(cardNumber, month);

        if (approvedList.isEmpty()) {
            throw new IllegalStateException("해당 월 승인 내역이 없습니다");
        }

        // 2. 총 청구금액 합산
        BigDecimal totalAmount = approvedList.stream()
                .map(AuthorizationHistoryResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. 카드번호 마스킹 (approval-service에서 이미 마스킹된 값)
        String cardNumberMasked = approvedList.get(0).getCardNumberMasked();

        // 4. billings 테이블 저장
        Billing billing = Billing.builder()
                .cardNumberMasked(cardNumberMasked)
                .billingMonth(month)
                .totalAmount(totalAmount)
                .transactionCount(approvedList.size())
                .status(BillingStatus.BILLED)
                .billedAt(LocalDateTime.now())
                .build();

        billingRepository.save(billing);

        log.info("청구서 생성 완료 - cardNumber: {}, totalAmount: {}",
                cardNumberMasked, totalAmount);

        return BillingResponse.builder()
                .cardNumberMasked(cardNumberMasked)
                .billingMonth(month)
                .totalAmount(totalAmount)
                .transactionCount(approvedList.size())
                .status(BillingStatus.BILLED.name())
                .billedAt(billing.getBilledAt())
                .build();
    }

    // 청구 내역 조회
    public List<BillingResponse> getBillingHistory(String cardNumber) {
        return billingRepository
                .findByCardNumberMaskedOrderByBillingMonthDesc(cardNumber)
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
}