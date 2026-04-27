package com.eum.inventoryserver.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QInventoryReservation is a Querydsl query type for InventoryReservation
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QInventoryReservation extends EntityPathBase<InventoryReservation> {

    private static final long serialVersionUID = -1740980434L;

    public static final QInventoryReservation inventoryReservation = new QInventoryReservation("inventoryReservation");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final ListPath<InventoryReservationItem, QInventoryReservationItem> items = this.<InventoryReservationItem, QInventoryReservationItem>createList("items", InventoryReservationItem.class, QInventoryReservationItem.class, PathInits.DIRECT2);

    public final NumberPath<Long> orderId = createNumber("orderId", Long.class);

    public final StringPath reason = createString("reason");

    public final StringPath sourceEventId = createString("sourceEventId");

    public final EnumPath<InventoryReservationStatus> status = createEnum("status", InventoryReservationStatus.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public QInventoryReservation(String variable) {
        super(InventoryReservation.class, forVariable(variable));
    }

    public QInventoryReservation(Path<? extends InventoryReservation> path) {
        super(path.getType(), path.getMetadata());
    }

    public QInventoryReservation(PathMetadata metadata) {
        super(InventoryReservation.class, metadata);
    }

}

