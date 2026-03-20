package com.card.payment.billing.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ItemReader: application.yml의 카드번호 목록을 순서대로 읽어옴
 * - read()가 null을 반환하면 Step이 종료됨
 * - @StepScope: Step 실행 시마다 새 인스턴스 생성 (index 초기화 보장)
 */
@Component
@StepScope
@Slf4j
public class BillingItemReader implements ItemReader<String> {

    @Value("${batch.billing.card-numbers}")
    private List<String> cardNumbers;

    private int index = 0;

    @Override
    public String read() {
        if (index < cardNumbers.size()) {
            String cardNumber = cardNumbers.get(index);
            log.info("카드번호 읽기 [{}/{}] - {}", index + 1, cardNumbers.size(), cardNumber);
            index++;
            return cardNumber;
        }
        return null; // null 반환 → Step 종료
    }
}
