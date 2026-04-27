package com.eum.boardserver.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "faq")
@Getter
@NoArgsConstructor
public class Faq {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "faq_seq_gen")
    @SequenceGenerator(name = "faq_seq_gen", sequenceName = "faq_seq", initialValue = 1, allocationSize = 50)
    @Column(name = "faq_id")
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private String author;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(nullable = false)
    private String category;

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned;

    @Type(JsonBinaryType.class)
    @Column(name = "content_image_urls", columnDefinition = "jsonb")
    private List<String> contentImageUrls = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "actions", columnDefinition = "jsonb")
    private List<Map<String, Object>> actions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public Faq(
            String title,
            String content,
            String author,
            Long viewCount,
            String category,
            Boolean isPinned,
            List<String> contentImageUrls,
            List<Map<String, Object>> actions
    ) {
        this.title = title;
        this.content = content;
        this.author = (author == null || author.isBlank()) ? "관리자" : author;
        this.viewCount = viewCount == null ? 0L : viewCount;
        this.category = (category == null || category.isBlank()) ? "FAQ" : category;
        this.isPinned = isPinned != null && isPinned;
        this.contentImageUrls = contentImageUrls != null ? contentImageUrls : new ArrayList<>();
        this.actions = actions != null ? actions : new ArrayList<>();
    }
}
