package com.card.payment.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingResponse {
    private String cardNumberMasked;
    private String billingMonth;
    private BigDecimal totalAmount;
    private Integer transactionCount;
    private String status;
    private LocalDateTime billedAt;
}