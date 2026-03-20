package com.card.payment.billing.client;

import com.card.payment.billing.dto.AuthorizationHistoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@Slf4j
public class AuthorizationClient {

    private final RestClient restClient;

    public AuthorizationClient(
            @Value("${approval.service.url}") String approvalUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(approvalUrl)
                .build();
    }

    public List<AuthorizationHistoryResponse> getMonthlyApproved(
            String cardNumber, String month) {

        // 카드번호 마스킹 처리
        String masked = maskCardNumber(cardNumber);

        log.info("승인 내역 조회 요청 - cardNumberMasked: {}, month: {}",
                masked, month);

        return restClient.get()
                .uri("/api/authorization/approved/monthly"
                        + "?cardNumberMasked=" + masked  // ← 수정!
                        + "&month=" + month)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
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