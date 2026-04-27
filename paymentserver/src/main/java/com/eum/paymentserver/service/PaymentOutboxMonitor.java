package com.eum.paymentserver.service;

import com.eum.paymentserver.domain.OutboxEventStatus;
import com.eum.paymentserver.domain.PaymentOutboxEvent;
import com.eum.paymentserver.repository.PaymentOutboxEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxMonitor {

    private static final int STALE_THRESHOLD_MINUTES = 5;

    private final PaymentOutboxEventRepository paymentOutboxEventRepository;

    @Scheduled(fixedDelay = 300_000)
    @Transactional(readOnly = true)
    public void checkStaleOutboxEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        List<PaymentOutboxEvent> stale = paymentOutboxEventRepository
                .findByStatusAndCreatedAtBefore(OutboxEventStatus.PENDING, threshold);

        if (stale.isEmpty()) {
            return;
        }

        log.warn("[OUTBOX-ALERT] {}개의 PENDING 아웃박스 이벤트가 {}분 이상 미발행 상태입니다. Debezium 커넥터를 확인하세요.",
                stale.size(), STALE_THRESHOLD_MINUTES);

        stale.forEach(event -> log.warn("[OUTBOX-ALERT] stale event: id={}, eventType={}, aggregateId={}, createdAt={}",
                event.getId(), event.getEventType(), event.getAggregateId(), event.getCreatedAt()));
    }
}
