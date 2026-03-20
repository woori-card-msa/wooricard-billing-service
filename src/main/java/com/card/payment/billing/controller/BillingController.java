package com.card.payment.billing.controller;

import com.card.payment.billing.dto.BillingResponse;
import com.card.payment.billing.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Billing", description = "매입 청구 API")
public class BillingController {

    private final BillingService billingService;

    @Operation(summary = "월별 청구서 생성",
            description = "카드번호와 청구월을 받아 청구서를 생성합니다.")
    @PostMapping("/monthly")
    public ResponseEntity<BillingResponse> createMonthlyBilling(
            @RequestParam String cardNumber,
            @RequestParam String month) {

        log.info("청구서 생성 요청 - cardNumber: {}, month: {}",
                cardNumber, month);
        BillingResponse response = billingService
                .createMonthlyBilling(cardNumber, month);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "청구 내역 조회",
            description = "카드번호로 전체 청구 내역을 조회합니다.")
    @GetMapping("/{cardNumber}")
    public ResponseEntity<List<BillingResponse>> getBillingHistory(
            @PathVariable String cardNumber) {

        log.info("청구 내역 조회 - cardNumber: {}", cardNumber);
        List<BillingResponse> response = billingService
                .getBillingHistory(cardNumber);
        return ResponseEntity.ok(response);
    }
}