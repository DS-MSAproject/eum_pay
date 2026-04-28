package com.eum.searchserver.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "asgard.public.products")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFallbackDocument {

    @Id
    private String documentId;

    @Field(name = "id", type = FieldType.Long)
    private Long productId;

    @Field(name = "product_name", type = FieldType.Text, index = false)
    private String productName;

    @Field(name = "title", type = FieldType.Text, index = false)
    private String title;

    @Field(name = "price", type = FieldType.Long)
    private Long price;

    @Field(name = "image_url", type = FieldType.Text, index = false)
    private String imageUrl;

    @Field(name = "created_at", type = FieldType.Long)
    private Long createdAt;
}
