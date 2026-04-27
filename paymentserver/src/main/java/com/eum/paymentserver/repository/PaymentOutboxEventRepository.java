package com.eum.paymentserver.repository;

import com.eum.paymentserver.domain.OutboxEventStatus;
import com.eum.paymentserver.domain.PaymentOutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOutboxEventRepository extends JpaRepository<PaymentOutboxEvent, Long> {

    List<PaymentOutboxEvent> findByStatusAndCreatedAtBefore(OutboxEventStatus status, LocalDateTime threshold);
}
