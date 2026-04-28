package com.eum.searchserver.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "asgard.public.reviews")
@Setting(settingPath = "static/settings.json")
@Mapping(mappingPath = "static/mappings-review.json")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSearchDocument {

    @Id
    private Long id;

    @Field(name = "public_id", type = FieldType.Keyword)
    private String publicId;

    @Field(name = "product_id", type = FieldType.Long)
    private Long productId;

    @MultiField(
            mainField = @Field(name = "writer_name", type = FieldType.Text, analyzer = "nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String writerName;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String content;

    @Field(type = FieldType.Integer)
    private Integer star;

    @Field(name = "like_count", type = FieldType.Long)
    private Long likeCount;

    @Field(name = "review_media_url", type = FieldType.Keyword, index = false)
    private String reviewMediaUrl;

    @Field(name = "review_media_urls", type = FieldType.Keyword, index = false)
    private String reviewMediaUrls;

    @Field(name = "media_type", type = FieldType.Keyword)
    private String mediaType;

    @Field(name = "created_at", type = FieldType.Long)
    private Long createdAt;

    @Field(name = "deleted_at", type = FieldType.Long)
    private Long deletedAt;

    @Field(name = "deleted_by", type = FieldType.Long)
    private Long deletedBy;

    @Field(name = "delete_reason", type = FieldType.Keyword)
    private String deleteReason;
}
