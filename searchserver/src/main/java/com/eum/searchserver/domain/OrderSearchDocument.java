package com.eum.searchserver.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "orderdb.public.orders")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSearchDocument {

    @Id
    private String id;

    @Field(name = "order_id", type = FieldType.Long)
    private Long orderId;

    @Field(name = "user_id", type = FieldType.Long)
    private Long userId;

    @Field(name = "amount", type = FieldType.Long)
    private Long amount;

    @Field(name = "order_state", type = FieldType.Keyword)
    private String orderState;

    @Field(name = "delete_yn", type = FieldType.Keyword)
    private String deleteYn;

    @Field(name = "time", type = FieldType.Long)
    private Long time;
}
