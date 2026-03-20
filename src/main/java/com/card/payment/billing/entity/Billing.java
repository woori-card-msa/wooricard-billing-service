package com.card.payment.billing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "billings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Billing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 카드번호 마스킹 (1234-****-****-5678)
    @Column(nullable = false, length = 19)
    private String cardNumberMasked;

    // 청구 년월 (2026-03)
    @Column(nullable = false, length = 7)
    private String billingMonth;

    // 총 청구금액
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    // 청구 건수
    @Column(nullable = false)
    private Integer transactionCount;

    // 청구 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingStatus status;

    // 청구일시
    @Column
    private LocalDateTime billedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}