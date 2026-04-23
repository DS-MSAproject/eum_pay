package com.eum.boardserver.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType; // 💡 JSONB 지원 라이브러리 활용 권장
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notice_seq_gen")
    @SequenceGenerator(name = "notice_seq_gen", sequenceName = "notice_seq", initialValue = 1, allocationSize = 50)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private String category; // [공지], [이벤트], [안내]

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned;

    // 🖼️ [추가] 상세 이미지 URL 리스트
    // PostgreSQL의 JSONB 타입을 사용하여 리스트를 효율적으로 저장합니다.
    @Type(JsonBinaryType.class)
    @Column(name = "content_image_urls", columnDefinition = "jsonb")
    private List<String> contentImageUrls = new ArrayList<>();

    // 🔗 [추가] 액션 버튼 리스트 (라벨, URL, 타입 등 포함)
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
    public Notice(String title, String content, String category, Boolean isPinned,
                  List<String> contentImageUrls, List<Map<String, Object>> actions) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.isPinned = (isPinned != null) ? isPinned : false;
        this.contentImageUrls = (contentImageUrls != null) ? contentImageUrls : new ArrayList<>();
        this.actions = (actions != null) ? actions : new ArrayList<>();
    }

    // [보정] 수정 메서드에 누락된 필드 추가
    public void update(String title, String content, String category, Boolean isPinned,
                       List<String> contentImageUrls, List<Map<String, Object>> actions) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.isPinned = isPinned;
        this.contentImageUrls = contentImageUrls;
        this.actions = actions;
    }
}