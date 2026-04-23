package com.eum.productserver.repository;

import com.eum.productserver.entity.Product;
import com.eum.productserver.entity.QProductImage;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page; // 수정: Hibernate Page 아님
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

import static com.eum.productserver.entity.QCategory.category;
import static com.eum.productserver.entity.QProduct.product;
import static com.eum.productserver.entity.QProductImage.productImage;


@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Product> findByCategoryId(Long categoryId, Pageable pageable) {

        List<Product> content = queryFactory
                .selectFrom(product)
                .leftJoin(product.category, category).fetchJoin()
                .where(
                        categoryIdEq(categoryId)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(product.createdDate.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(product.count())
                .from(product)
                .where(categoryIdEq(categoryId));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }



    @Override
    public List<Product> findTop6ByOrderBySalesCountDesc() {
        return queryFactory
                .selectFrom(product)
                // fetchJoin은 1:N에서 페이징할 때만 위험하고,
                // 이렇게 limit이 작고 단순 조회일 때는 성능상 쓰는 게 좋습니다.
                .leftJoin(product.images, productImage).fetchJoin()
                .where(productImage.isMain.isTrue())
                .orderBy(product.salesCount.desc())
                .limit(6)
                .fetch();
    }

    private BooleanExpression categoryIdEq(Long categoryId) {
        if (categoryId == null) return null;
        return product.category.id.eq(categoryId)
                .or(product.category.parent.id.eq(categoryId));
    }
}
