package com.eum.productserver.repository;

import com.eum.productserver.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    // 1. 특정 카테고리에 속한 상품 존재 여부
    boolean existsByCategoryId(Long categoryId); // 엔티티 필드명에 맞춰 수정 권장

    boolean existsByProductUrl(String productUrl);

    boolean existsByProductNameAndCategory_Id(String productName, Long categoryId);

    Optional<Product> findByProductNameAndCategory_Id(String productName, Long categoryId);

    // 2. 브랜드별 상품 목록 조회 (최신순)
    List<Product> findByBrandIdOrderByCreatedDateDesc(Long brandId);

    // 2. 품절된 것만 제외하고 가져오고 싶을 때 (기존 로직과 가장 유사)
    List<Product> findByStatusNot(Product.ProductStatus status);

    List<Product> findByProductIdGreaterThanOrderByProductIdAsc(Long lastProductId, Pageable pageable);
}
