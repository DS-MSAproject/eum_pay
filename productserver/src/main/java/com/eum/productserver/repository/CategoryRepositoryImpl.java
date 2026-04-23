package com.eum.productserver.repository;


import com.eum.productserver.entity.Category;
import com.eum.productserver.entity.QCategory;
import com.eum.productserver.entity.QProduct;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepositoryCustom {

    private final  JPAQueryFactory queryFactory;

    @Override
    public List<Category> findAllWithProducts() { // 메서드명도 도메인에 맞게 변경 권장
        QCategory category = QCategory.category;
        QProduct product = QProduct.product;

        return queryFactory
                .selectFrom(category)
                // 카테고리에 속한 상품들을 미리 가져오고 싶다면 fetchJoin 사용
                // (단일 계층이므로 parent/child 조인은 삭제합니다)
                .leftJoin(category.parent).fetchJoin()
                .leftJoin(category.products, product).fetchJoin()
                .distinct()
                .fetch();
    }
}
