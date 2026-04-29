package com.eum.paymentserver.repository;

import com.eum.paymentserver.domain.Payment;
import com.eum.paymentserver.domain.PaymentAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByPaymentOrderByCreatedAtAsc(Payment payment);

    // CONFIRM 시도가 2회 이상인 결제 — 중복 요청 의심
    @Query("""
            SELECT a.payment FROM PaymentAttempt a
            WHERE a.requestType = 'CONFIRM'
            GROUP BY a.payment
            HAVING COUNT(a) > 1
            """)
    List<Payment> findPaymentsWithMultipleConfirmAttempts();
}
