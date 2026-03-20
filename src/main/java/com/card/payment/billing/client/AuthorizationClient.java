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

        log.info("승인 내역 조회 요청 - cardNumber: {}, month: {}",
                cardNumber, month);

        return restClient.get()
                .uri("/api/authorization/approved/monthly"
                        + "?cardNumber=" + cardNumber
                        + "&month=" + month)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}