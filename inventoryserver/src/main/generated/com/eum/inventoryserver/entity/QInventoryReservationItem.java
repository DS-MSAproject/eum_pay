package com.eum.inventoryserver.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QInventoryReservationItem is a Querydsl query type for InventoryReservationItem
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QInventoryReservationItem extends EntityPathBase<InventoryReservationItem> {

    private static final long serialVersionUID = 1903060833L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QInventoryReservationItem inventoryReservationItem = new QInventoryReservationItem("inventoryReservationItem");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Long> optionId = createNumber("optionId", Long.class);

    public final StringPath optionName = createString("optionName");

    public final NumberPath<Long> price = createNumber("price", Long.class);

    public final NumberPath<Long> productId = createNumber("productId", Long.class);

    public final StringPath productName = createString("productName");

    public final NumberPath<Integer> quantity = createNumber("quantity", Integer.class);

    public final QInventoryReservation reservation;

    public QInventoryReservationItem(String variable) {
        this(InventoryReservationItem.class, forVariable(variable), INITS);
    }

    public QInventoryReservationItem(Path<? extends InventoryReservationItem> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QInventoryReservationItem(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QInventoryReservationItem(PathMetadata metadata, PathInits inits) {
        this(InventoryReservationItem.class, metadata, inits);
    }

    public QInventoryReservationItem(Class<? extends InventoryReservationItem> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.reservation = inits.isInitialized("reservation") ? new QInventoryReservation(forProperty("reservation")) : null;
    }

}

