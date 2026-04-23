package com.eum.cartserver.repository;

import com.eum.cartserver.domain.CartItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ci from CartItem ci
             where ci.cart.id = :cartId
               and ci.productId = :productId
               and (
                    (:optionId is null and ci.optionId is null)
                    or ci.optionId = :optionId
               )
            """)
    Optional<CartItem> findByBusinessKeyWithLock(
            @Param("cartId") Long cartId,
            @Param("productId") Long productId,
            @Param("optionId") Long optionId
    );

    List<CartItem> findAllByCart_IdOrderByCreatedAtAsc(Long cartId);

    @Modifying
    @Query(value = """
            INSERT INTO cart_item (cart_id, item_id, option_id, quantity, is_selected, created_at, updated_at)
            VALUES (:cartId, :productId, :optionId, :quantity, :selected, :createdAt, :updatedAt)
            ON CONFLICT (cart_id, item_id, option_id) WHERE option_id IS NOT NULL
            DO UPDATE SET quantity = cart_item.quantity + EXCLUDED.quantity,
                          is_selected = cart_item.is_selected OR EXCLUDED.is_selected,
                          updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertQuantity(@Param("cartId") Long cartId,
                       @Param("productId") Long productId,
                       @Param("optionId") Long optionId,
                       @Param("quantity") Long quantity,
                       @Param("selected") boolean selected,
                       @Param("createdAt") LocalDateTime createdAt,
                       @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query(value = """
            INSERT INTO cart_item (cart_id, item_id, option_id, quantity, is_selected, created_at, updated_at)
            VALUES (:cartId, :productId, NULL, :quantity, :selected, :createdAt, :updatedAt)
            ON CONFLICT (cart_id, item_id) WHERE option_id IS NULL
            DO UPDATE SET quantity = cart_item.quantity + EXCLUDED.quantity,
                          is_selected = cart_item.is_selected OR EXCLUDED.is_selected,
                          updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertQuantityWithoutOption(@Param("cartId") Long cartId,
                                    @Param("productId") Long productId,
                                    @Param("quantity") Long quantity,
                                    @Param("selected") boolean selected,
                                    @Param("createdAt") LocalDateTime createdAt,
                                    @Param("updatedAt") LocalDateTime updatedAt);
}
