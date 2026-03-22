package com.card.payment.billing.client;

import com.card.payment.billing.dto.AuthorizationHistoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * OpenFeign이 이 인터페이스를 보고 자동으로 HTTP 클라이언트(구현체)를 만들어줌
 *
 * name = "wooricard-approval-service"
 *   → Eureka에 등록된 서비스 이름
 *   → URL 하드코딩 없이
 *     Eureka가 자동으로 주소 찾아줌
 *
 */
@FeignClient(name = "wooricard-approval-service")
public interface AuthorizationClient {

    // GET http://유림IP:8081/api/authorization/approved/monthly
    @GetMapping("/api/authorization/approved/monthly")
    List<AuthorizationHistoryResponse> getMonthlyApproved(
            @RequestParam String cardNumberMasked,
            @RequestParam String month
    );
}