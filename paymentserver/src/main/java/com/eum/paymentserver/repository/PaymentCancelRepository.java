package com.eum.paymentserver.repository;

import com.eum.paymentserver.domain.PaymentCancel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {
}
