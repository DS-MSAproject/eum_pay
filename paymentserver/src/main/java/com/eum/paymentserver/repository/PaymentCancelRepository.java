package com.eum.paymentserver.repository;

import com.eum.paymentserver.domain.PaymentCancel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

    @Query("SELECT COALESCE(SUM(c.cancelAmount), 0) FROM PaymentCancel c WHERE c.cancelStatus = 'DONE'")
    Long sumCanceledAmount();
}
