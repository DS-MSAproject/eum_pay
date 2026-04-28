package com.eum.reviewserver.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QReviewHelpful is a Querydsl query type for ReviewHelpful
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QReviewHelpful extends EntityPathBase<ReviewHelpful> {

    private static final long serialVersionUID = -1926215154L;

    public static final QReviewHelpful reviewHelpful = new QReviewHelpful("reviewHelpful");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Long> reviewId = createNumber("reviewId", Long.class);

    public final NumberPath<Long> userId = createNumber("userId", Long.class);

    public QReviewHelpful(String variable) {
        super(ReviewHelpful.class, forVariable(variable));
    }

    public QReviewHelpful(Path<? extends ReviewHelpful> path) {
        super(path.getType(), path.getMetadata());
    }

    public QReviewHelpful(PathMetadata metadata) {
        super(ReviewHelpful.class, metadata);
    }

}

