package com.eum.productserver.repository;

import com.eum.productserver.entity.Product;
import org.springframework.data.domain.Page; // 수정: Hibernate Page 아님
import org.springframework.data.domain.Pageable; // 수정: java.awt.print.Pageable 아님
import java.util.List;

public interface ProductRepositoryCustom {

    // 특정 카테고리 상품을 최신순/판매량순으로 페이징 조회
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    // 판매량 높은 순으로 상위 6개 추출
    List<Product> findTop6ByOrderBySalesCountDesc();
}
