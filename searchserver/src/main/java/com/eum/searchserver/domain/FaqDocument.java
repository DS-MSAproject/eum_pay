package com.eum.searchserver.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.List;

@Document(indexName = "asgard.public.faq")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setting(settingPath = "static/settings.json")
@Mapping(mappingPath = "static/mappings-faq.json")
public class FaqDocument {

    @Id
    @Field(name = "faq_id", type = FieldType.Long)
    private Long id;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword),
                    @InnerField(suffix = "edge", type = FieldType.Text, analyzer = "edge_analyzer"),
                    @InnerField(suffix = "partial", type = FieldType.Text, analyzer = "ngram_analyzer")
            }
    )
    private String title;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "edge", type = FieldType.Text, analyzer = "edge_analyzer"),
                    @InnerField(suffix = "partial", type = FieldType.Text, analyzer = "ngram_analyzer")
            }
    )
    private String content;

    @Field(type = FieldType.Keyword, normalizer = "lowercase_normalizer")
    private String category;

    @Field(type = FieldType.Keyword)
    private String author;

    @Field(name = "view_count", type = FieldType.Long)
    private Long viewCount;

    @Field(name = "is_pinned", type = FieldType.Boolean)
    private Boolean isPinned;

    @Field(name = "content_image_urls", type = FieldType.Keyword, index = false)
    private List<String> contentImageUrls;

    @Field(type = FieldType.Object)
    private List<FaqActionRecord> actions;

    @Field(name = "created_at", type = FieldType.Date, format = DateFormat.date_optional_time)
    private String createdAt;

    @Field(name = "updated_at", type = FieldType.Date, format = DateFormat.date_optional_time)
    private String updatedAt;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaqActionRecord {
        private String label;
        @Field(name = "target_url")
        private String targetUrl;
        @Field(name = "action_type")
        private String actionType;
        @Field(name = "sort_order")
        private Integer sortOrder;
    }
}
