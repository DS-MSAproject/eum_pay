package com.eum.reviewserver.repository;

import static com.eum.reviewserver.entity.QReview.review;

import com.eum.reviewserver.entity.Review;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class ReviewRepositoryImpl implements ReviewRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public ReviewRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<Review> findPageByCondition(
            Long productId,
            String keyword,
            String sortType,
            String reviewType,
            Pageable pageable
    ) {
        BooleanExpression condition = review.productId.eq(productId)
                .and(activeReview())
                .and(keywordContains(keyword))
                .and(reviewTypeEq(reviewType));

        List<Review> content = queryFactory
                .selectFrom(review)
                .where(condition)
                .orderBy(orderBy(sortType))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(review.count())
                .from(review)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    @Override
    public List<Review> findAllByProductId(Long productId) {
        return queryFactory
                .selectFrom(review)
                .where(review.productId.eq(productId), activeReview())
                .fetch();
    }

    @Override
    public boolean existsByProductIdAndWriterId(Long productId, Long writerId) {
        Integer found = queryFactory
                .selectOne()
                .from(review)
                .where(review.productId.eq(productId), review.writerId.eq(writerId))
                .fetchFirst();
        return found != null;
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return review.content.containsIgnoreCase(keyword)
                .or(review.writerName.containsIgnoreCase(keyword));
    }

    private BooleanExpression activeReview() {
        return review.deletedAt.isNull();
    }

    private BooleanExpression reviewTypeEq(String reviewType) {
        if (!StringUtils.hasText(reviewType) || "ALL".equalsIgnoreCase(reviewType)) {
            return null;
        }
        return switch (reviewType.toUpperCase()) {
            case "PHOTO", "IMAGE" -> review.mediaType.eq("IMAGE");
            case "VIDEO" -> review.mediaType.eq("VIDEO");
            case "TEXT" -> review.mediaType.eq("TEXT");
            default -> null;
        };
    }

    private OrderSpecifier<?>[] orderBy(String sortType) {
        if ("BEST".equalsIgnoreCase(sortType)) {
            return new OrderSpecifier[]{
                    review.likeCount.desc(),
                    review.star.desc(),
                    review.createdAt.desc()
            };
        }
        return new OrderSpecifier[]{review.createdAt.desc()};
    }
}
