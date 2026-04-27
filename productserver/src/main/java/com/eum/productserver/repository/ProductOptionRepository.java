package com.eum.productserver.repository;

import com.eum.productserver.entity.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    //  1. 특정 상품의 모든 옵션 조회
    // Product 엔티티 내부의 productId 필드명을 따라가야 합니다.
    List<ProductOption> findByProduct_ProductId(Long productId);

    //  2. 옵션명(option)으로 특정 옵션 찾기
    Optional<ProductOption> findByProduct_ProductIdAndOptionName(Long productId, @Nullable String optionName);
}
