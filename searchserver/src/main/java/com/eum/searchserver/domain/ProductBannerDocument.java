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
public class ProductBannerDocument {

    @Id
    private String documentId;

    @Field(name = "product_id", type = FieldType.Long)
    private Long productId;

    @Field(name = "image_url", type = FieldType.Text, index = false)
    private String imageUrl;

    @Field(name = "created_at", type = FieldType.Long)
    private Long createdAt;
}
