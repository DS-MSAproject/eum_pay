package com.eum.inventoryserver.repository;

import com.eum.inventoryserver.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // 1. 상품ID와 옵션ID로 특정 재고 조회 (일반 조회)
    // optionId가 null인 경우는 상품 본체의 재고를 찾습니다.
    Optional<Inventory> findByProductIdAndOptionId(Long productId, Long optionId);

    List<Inventory> findAllByProductId(Long productId);

    // 2. 비관적 락을 적용한 조회 (차감/복구 시 사용)
    // JPQL을 사용하여 optionId가 null인 경우와 값이 있는 경우를 모두 처리합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.productId = :productId " +
            "and (:optionId is null and i.optionId is null or i.optionId = :optionId)")
    Optional<Inventory> findByProductIdAndOptionIdWithLock(
            @Param("productId") Long productId,
            @Param("optionId") Long optionId);

    // 3. 특정 상품 ID 리스트에 해당하는 모든 재고 정보 조회 (Batch 조회용)
    // 이 메서드는 그대로 두셔도 됩니다. (Service에서 합산 로직 처리)
    List<Inventory> findAllByProductIdIn(List<Long> productIds);

    long deleteByProductId(Long productId);

    @Modifying
    @Query("delete from Inventory i where i.productId not in :productIds")
    long deleteByProductIdNotIn(@Param("productIds") List<Long> productIds);
}
