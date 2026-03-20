package com.card.payment.billing.client;

import com.card.payment.billing.dto.AuthorizationHistoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "wooricard-approval-service",
        url = "${approval.service.url}"
)
public interface AuthorizationClient {

    @GetMapping("/api/authorization/approved/monthly")
    List<AuthorizationHistoryResponse> getMonthlyApproved(
            @RequestParam String cardNumberMasked,
            @RequestParam String month
    );
}