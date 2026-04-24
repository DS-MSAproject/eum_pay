package com.eum.cartserver.repository;

import com.eum.cartserver.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO cart (user_id, created_at, updated_at)
            VALUES (:userId, :createdAt, :updatedAt)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int insertCartIfAbsent(@Param("userId") Long userId,
                           @Param("createdAt") LocalDateTime createdAt,
                           @Param("updatedAt") LocalDateTime updatedAt);
}
