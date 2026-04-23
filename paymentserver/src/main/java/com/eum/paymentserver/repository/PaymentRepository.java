package com.eum.paymentserver.repository;

import com.eum.paymentserver.domain.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByPaymentId(String paymentId);

    Optional<Payment> findByPaymentKey(String paymentKey);
}
