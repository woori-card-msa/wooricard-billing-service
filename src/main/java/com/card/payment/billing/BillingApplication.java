package com.card.payment.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 카드 매입 청구 서비스 애플리케이션
 */
@SpringBootApplication
@EnableJpaAuditing  // createdAt 자동 저장
@EnableFeignClients  // OpenFeign 활성화
public class BillingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
