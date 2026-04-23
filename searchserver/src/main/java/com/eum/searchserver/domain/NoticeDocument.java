package com.eum.searchserver.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.List;

@Document(indexName = "asgard.public.notices")
@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
@Setting(settingPath = "static/settings.json")
@Mapping(mappingPath = "static/mappings1.json")
public class NoticeDocument {

    @Id
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

    @Field(name = "is_pinned", type = FieldType.Boolean)
    private Boolean isPinned;

    // 💡 index=false로 성능을 챙기고, name을 명확히 함
    @Field(name = "content_image_urls", type = FieldType.Keyword, index = false)
    private List<String> contentImageUrls;

    // 💡 다중 액션 버튼 리스트
    @Field(type = FieldType.Object)
    private List<NoticeActionRecord> actions;

    @Field(name = "created_at", type = FieldType.Date, format = DateFormat.date_optional_time)
    private String createdAt;

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class NoticeActionRecord {
        private String label;
        @Field(name = "target_url") // 💡 내부 필드도 snake_case 대응
        private String targetUrl;
        @Field(name = "action_type")
        private String actionType;
        @Field(name = "sort_order")
        private Integer sortOrder;
    }
}
