package com.eum.productserver.repository;

import com.eum.productserver.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long>, CategoryRepositoryCustom{

    // 1. 최상위 카테고리(대분류)만 조회 (parent가 null 인 것)
    // 정렬 순서 (displayOrder)가 있다면 정렬해서 가져오는 게 좋습니다.
    List<Category> findByParentIsNullOrderByDisplayOrderAsc();

    // 2. 특정 부모 카테고리에 속한 하위 카테고리(소분류) 조회
    List<Category> findByParentIdOrderByDisplayOrderAsc(Long parentId);

    // 3. 중복 체크
    boolean existsByCategoryName(String categoryName);

    // 4. 이름으로 조회  
    Optional<Category> findByCategoryName(String categoryName);

    // 5. 전체 조회 시 성능 최적화 (N+1 방지)
    @Query("""
            SELECT DISTINCT c
            FROM Category c
            LEFT JOIN FETCH c.children child
            WHERE c.parent IS NULL
            ORDER BY c.displayOrder ASC, child.displayOrder ASC
            """)
    List<Category> findAllWithChildren();
}
