package com.eum.cartserver.repository;

import com.eum.cartserver.domain.CartItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Slice<CartItem> findByCart_IdOrderByCreatedAtDescIdDesc(Long cartId, Pageable pageable);

    long countByCart_IdAndSelectedTrue(Long cartId);

    boolean existsByCart_Id(Long cartId);

    boolean existsByCart_IdAndSelectedFalse(Long cartId);
}
