package com.eum.searchserver.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Document(indexName = "asgard.public.products")
@Setting(settingPath = "static/settings.json")
@Mapping(mappingPath = "static/mappings.json")
@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ProductDocument {

    @Id
    private Long id;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "edge", type = FieldType.Text, analyzer = "edge_analyzer"),
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String title;

    @Field(name = "product_name", type = FieldType.Text, index = false)
    private String productName;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "partial", type = FieldType.Text, analyzer = "ngram_analyzer")
            }
    )
    private String keywords;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(name = "category_id", type = FieldType.Long)
    private Long categoryId;

    @Field(name = "brand_name", type = FieldType.Text)
    private String brandName;

    @Field(name = "sub_category", type = FieldType.Keyword) // 💡 name 명시
    private String subCategory;

    @Field(type = FieldType.Text, index = false)
    private String content;

    @Field(type = FieldType.Long)
    private Long price;

    @Field(name = "original_price", type = FieldType.Long) // 💡 할인율 계산용 핵심 필드
    private Long originalPrice;

    @Field(name = "image_url", type = FieldType.Text, index = false) // 💡 name 명시
    private String imageUrl;

    @Field(name = "sales_count", type = FieldType.Long)
    private Long salesCount;

    @Field(name = "sales_rank", type = FieldType.Integer)
    private Integer salesRank;

    @Field(name = "view_count", type = FieldType.Long)
    private Long viewCount;

    // 💡 format을 서비스 레이어(LocalDateTime)와 일치시킴
    @Field(name = "created_at", type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private String createdAt;
}
