package com.eum.reviewserver.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QReview is a Querydsl query type for Review
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QReview extends EntityPathBase<Review> {

    private static final long serialVersionUID = 1522383022L;

    public static final QReview review = new QReview("review");

    public final StringPath content = createString("content");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> deletedAt = createDateTime("deletedAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> deletedBy = createNumber("deletedBy", Long.class);

    public final StringPath deleteReason = createString("deleteReason");

    public final NumberPath<Integer> freshnessScore = createNumber("freshnessScore", Integer.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Long> likeCount = createNumber("likeCount", Long.class);

    public final StringPath mediaType = createString("mediaType");

    public final NumberPath<Integer> preferenceScore = createNumber("preferenceScore", Integer.class);

    public final NumberPath<Long> productId = createNumber("productId", Long.class);

    public final ComparablePath<java.util.UUID> publicId = createComparable("publicId", java.util.UUID.class);

    public final NumberPath<Integer> repurchaseScore = createNumber("repurchaseScore", Integer.class);

    public final ListPath<ReviewMediaPayload, SimplePath<ReviewMediaPayload>> reviewMediaJson = this.<ReviewMediaPayload, SimplePath<ReviewMediaPayload>>createList("reviewMediaJson", ReviewMediaPayload.class, SimplePath.class, PathInits.DIRECT2);

    public final StringPath reviewMediaUrl = createString("reviewMediaUrl");

    public final StringPath reviewMediaUrls = createString("reviewMediaUrls");

    public final NumberPath<Integer> star = createNumber("star", Integer.class);

    public final NumberPath<Long> writerId = createNumber("writerId", Long.class);

    public final StringPath writerName = createString("writerName");

    public QReview(String variable) {
        super(Review.class, forVariable(variable));
    }

    public QReview(Path<? extends Review> path) {
        super(path.getType(), path.getMetadata());
    }

    public QReview(PathMetadata metadata) {
        super(Review.class, metadata);
    }

}

