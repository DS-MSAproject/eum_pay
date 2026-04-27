package com.eum.reviewserver.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reviews_product_writer", columnNames = {"product_id", "writer_id"})
        }
)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true)
    private UUID publicId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "writer_id", nullable = false)
    private Long writerId;

    @Column(name = "writer_name", nullable = false, length = 100)
    private String writerName;

    @Column(nullable = false)
    private Integer star;

    @Column(name = "preference_score")
    private Integer preferenceScore;

    @Column(name = "repurchase_score", nullable = false)
    private Integer repurchaseScore;

    @Column(name = "freshness_score")
    private Integer freshnessScore;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "review_media_url", length = 1000)
    private String reviewMediaUrl;

    @Column(name = "review_media_urls", length = 4000)
    private String reviewMediaUrls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_media_json", columnDefinition = "jsonb")
    private List<ReviewMediaPayload> reviewMediaJson;

    @Column(name = "media_type", length = 20)
    private String mediaType;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "delete_reason", length = 255)
    private String deleteReason;

    @PrePersist
    public void prePersist() {
        if (publicId == null) {
            publicId = UuidCreator.getTimeOrderedEpoch();
        }
        if (likeCount == null) {
            likeCount = 0L;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public void setPublicId(UUID publicId) {
        this.publicId = publicId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getWriterId() {
        return writerId;
    }

    public void setWriterId(Long writerId) {
        this.writerId = writerId;
    }

    public String getWriterName() {
        return writerName;
    }

    public void setWriterName(String writerName) {
        this.writerName = writerName;
    }

    public Integer getStar() {
        return star;
    }

    public void setStar(Integer star) {
        this.star = star;
    }

    public Integer getPreferenceScore() {
        return preferenceScore;
    }

    public void setPreferenceScore(Integer preferenceScore) {
        this.preferenceScore = preferenceScore;
    }

    public Integer getRepurchaseScore() {
        return repurchaseScore;
    }

    public void setRepurchaseScore(Integer repurchaseScore) {
        this.repurchaseScore = repurchaseScore;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReviewMediaUrl() {
        return reviewMediaUrl;
    }

    public void setReviewMediaUrl(String reviewMediaUrl) {
        this.reviewMediaUrl = reviewMediaUrl;
    }

    public String getReviewMediaUrls() {
        return reviewMediaUrls;
    }

    public void setReviewMediaUrls(String reviewMediaUrls) {
        this.reviewMediaUrls = reviewMediaUrls;
    }

    public List<ReviewMediaPayload> getReviewMediaJson() {
        return reviewMediaJson;
    }

    public void setReviewMediaJson(List<ReviewMediaPayload> reviewMediaJson) {
        this.reviewMediaJson = reviewMediaJson;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Integer getFreshnessScore() {
        return freshnessScore;
    }

    public void setFreshnessScore(Integer freshnessScore) {
        this.freshnessScore = freshnessScore;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public Long getDeletedBy() {
        return deletedBy;
    }

    public String getDeleteReason() {
        return deleteReason;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted(Long deletedBy, String deleteReason) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
        this.deleteReason = deleteReason;
    }
}
