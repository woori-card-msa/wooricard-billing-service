package com.card.payment.billing.repository;

import com.card.payment.billing.entity.Billing;
import com.card.payment.billing.entity.BillingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingRepository extends JpaRepository<Billing, Long> {

    // 카드번호 + 청구월로 조회
    Optional<Billing> findByCardNumberMaskedAndBillingMonth(
            String cardNumberMasked, String billingMonth);

    // 카드번호로 전체 청구 내역 조회
    List<Billing> findByCardNumberMaskedOrderByBillingMonthDesc(
            String cardNumberMasked);

    // 상태로 조회
    List<Billing> findByStatus(BillingStatus status);
}