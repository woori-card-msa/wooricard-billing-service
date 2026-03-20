package com.card.payment.billing.batch;

import com.card.payment.billing.entity.Billing;
import com.card.payment.billing.repository.BillingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * ItemWriter: Billing 엔티티 목록을 DB에 저장
 * - chunk 단위로 묶어서 한 번에 saveAll (성능 최적화)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillingItemWriter implements ItemWriter<Billing> {

    private final BillingRepository billingRepository;

    @Override
    public void write(Chunk<? extends Billing> chunk) {
        billingRepository.saveAll(chunk.getItems());
        log.info("청구서 저장 완료 - {}건", chunk.getItems().size());
    }
}
