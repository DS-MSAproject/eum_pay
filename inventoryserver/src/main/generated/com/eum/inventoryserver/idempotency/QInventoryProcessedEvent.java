package com.eum.inventoryserver.idempotency;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QInventoryProcessedEvent is a Querydsl query type for InventoryProcessedEvent
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QInventoryProcessedEvent extends EntityPathBase<InventoryProcessedEvent> {

    private static final long serialVersionUID = -401450562L;

    public static final QInventoryProcessedEvent inventoryProcessedEvent = new QInventoryProcessedEvent("inventoryProcessedEvent");

    public final StringPath eventId = createString("eventId");

    public final StringPath eventType = createString("eventType");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final DateTimePath<java.time.LocalDateTime> processedAt = createDateTime("processedAt", java.time.LocalDateTime.class);

    public QInventoryProcessedEvent(String variable) {
        super(InventoryProcessedEvent.class, forVariable(variable));
    }

    public QInventoryProcessedEvent(Path<? extends InventoryProcessedEvent> path) {
        super(path.getType(), path.getMetadata());
    }

    public QInventoryProcessedEvent(PathMetadata metadata) {
        super(InventoryProcessedEvent.class, metadata);
    }

}

