package com.eum.paymentserver.repository;

import com.eum.paymentserver.domain.Payment;
import com.eum.paymentserver.domain.PaymentState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByPaymentId(String paymentId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status")
    Long sumAmountByStatus(@Param("status") PaymentState status);

    long countByStatus(PaymentState status);
}
