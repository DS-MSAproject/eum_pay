package com.eum.searchserver.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "orderdb.public.order_details")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailSearchDocument {

    @Id
    private String id;

    @Field(name = "detail_id", type = FieldType.Long)
    private Long detailId;

    @Field(name = "order_id", type = FieldType.Long)
    private Long orderId;

    @Field(name = "item_id", type = FieldType.Long)
    private Long productId;

    @Field(name = "amount", type = FieldType.Long)
    private Long quantity;

    @Field(name = "total_price", type = FieldType.Long)
    private Long totalPrice;
}
