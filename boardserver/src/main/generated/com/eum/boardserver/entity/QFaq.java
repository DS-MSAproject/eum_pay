package com.eum.boardserver.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QFaq is a Querydsl query type for Faq
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QFaq extends EntityPathBase<Faq> {

    private static final long serialVersionUID = 726399918L;

    public static final QFaq faq = new QFaq("faq");

    public final ListPath<java.util.Map<String, Object>, SimplePath<java.util.Map<String, Object>>> actions = this.<java.util.Map<String, Object>, SimplePath<java.util.Map<String, Object>>>createList("actions", java.util.Map.class, SimplePath.class, PathInits.DIRECT2);

    public final StringPath author = createString("author");

    public final StringPath category = createString("category");

    public final StringPath content = createString("content");

    public final ListPath<String, StringPath> contentImageUrls = this.<String, StringPath>createList("contentImageUrls", String.class, StringPath.class, PathInits.DIRECT2);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final BooleanPath isPinned = createBoolean("isPinned");

    public final StringPath title = createString("title");

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> viewCount = createNumber("viewCount", Long.class);

    public QFaq(String variable) {
        super(Faq.class, forVariable(variable));
    }

    public QFaq(Path<? extends Faq> path) {
        super(path.getType(), path.getMetadata());
    }

    public QFaq(PathMetadata metadata) {
        super(Faq.class, metadata);
    }

}

