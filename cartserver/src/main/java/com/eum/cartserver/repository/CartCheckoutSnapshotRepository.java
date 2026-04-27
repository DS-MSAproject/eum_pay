package com.eum.cartserver.repository;

import com.eum.cartserver.domain.CartCheckoutSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartCheckoutSnapshotRepository extends JpaRepository<CartCheckoutSnapshot, Long> {

    Optional<CartCheckoutSnapshot> findByOrderId(Long orderId);

    void deleteByOrderId(Long orderId);
}
