package com.card.payment.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationHistoryResponse {
    private String transactionId;
    private String cardNumberMasked;
    private BigDecimal amount;
    private String merchantId;
    private LocalDateTime authorizationDate;
    private String status;
}