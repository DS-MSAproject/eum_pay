package com.eum.searchserver.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "asgard.public.product_options")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductOptionDocument {

    @Id
    @Field(name = "option_id", type = FieldType.Long)
    private Long optionId;

    @Field(name = "product_id", type = FieldType.Long)
    private Long productId;

    @Field(name = "option_name", type = FieldType.Text)
    private String optionName;

    @Field(name = "extra_price", type = FieldType.Long)
    private Long extraPrice;

    @Field(name = "initial_stock", type = FieldType.Integer)
    private Integer initialStock;
}
