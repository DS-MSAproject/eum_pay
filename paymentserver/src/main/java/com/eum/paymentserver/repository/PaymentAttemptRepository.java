package com.eum.paymentserver.repository;

import com.eum.paymentserver.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
}
