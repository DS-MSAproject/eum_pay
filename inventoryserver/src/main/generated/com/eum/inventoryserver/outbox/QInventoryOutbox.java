package com.eum.inventoryserver.outbox;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QInventoryOutbox is a Querydsl query type for InventoryOutbox
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QInventoryOutbox extends EntityPathBase<InventoryOutbox> {

    private static final long serialVersionUID = 1151098613L;

    public static final QInventoryOutbox inventoryOutbox = new QInventoryOutbox("inventoryOutbox");

    public final NumberPath<Long> aggregateId = createNumber("aggregateId", Long.class);

    public final StringPath aggregateType = createString("aggregateType");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath eventId = createString("eventId");

    public final StringPath eventType = createString("eventType");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath payload = createString("payload");

    public final StringPath topic = createString("topic");

    public QInventoryOutbox(String variable) {
        super(InventoryOutbox.class, forVariable(variable));
    }

    public QInventoryOutbox(Path<? extends InventoryOutbox> path) {
        super(path.getType(), path.getMetadata());
    }

    public QInventoryOutbox(PathMetadata metadata) {
        super(InventoryOutbox.class, metadata);
    }

}

