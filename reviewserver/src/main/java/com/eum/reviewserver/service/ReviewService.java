package com.eum.reviewserver.service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.eum.reviewserver.dto.request.CreateReviewRequest;
import com.eum.reviewserver.dto.request.UpdateReviewRequest;
import com.eum.reviewserver.dto.response.ReviewCreateDataDto;
import com.eum.reviewserver.dto.response.ReviewCreateResponse;
import com.eum.reviewserver.dto.response.ReviewDeleteResponse;
import com.eum.reviewserver.dto.response.ReviewDetailDto;
import com.eum.reviewserver.dto.response.ReviewDetailResponse;
import com.eum.reviewserver.dto.response.ReviewMediaDto;
import com.eum.reviewserver.dto.response.ReviewUpdateResponse;
import com.eum.reviewserver.entity.ReviewMediaPayload;
import com.eum.reviewserver.entity.Review;
import com.eum.reviewserver.exception.ConflictException;
import com.eum.reviewserver.exception.PayloadTooLargeException;
import com.eum.reviewserver.exception.ResourceNotFoundException;
import com.eum.reviewserver.exception.UnauthorizedException;
import com.eum.reviewserver.repository.ReviewRepository;
import com.eum.s3.S3Component;
import com.eum.s3.S3Directory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 5;
    private static final int MAX_VIDEO_COUNT = 1;
    private static final String REVIEW_API_PATH = "/api/v1/reviews";
    private static final String MEDIA_URL_DELIMITER = "|";
    private static final String DEFAULT_DELETE_REASON = "USER_REQUEST";

    private final ReviewRepository reviewRepository;
    private final S3Component s3Component;

    public ReviewService(ReviewRepository reviewRepository,
                         S3Component s3Component) {
        this.reviewRepository = reviewRepository;
        this.s3Component = s3Component;
    }

    @Transactional
    public ReviewDetailResponse getReviewDetail(UUID publicId, Boolean isInterested) {
        Review review = reviewRepository.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        ensurePublicId(review);

        if (Boolean.TRUE.equals(isInterested)) {
            review.setLikeCount(review.getLikeCount() + 1);
        }

        return new ReviewDetailResponse("success", toDetailDto(review));
    }

    @Transactional
    public ReviewCreateResponse createReview(
            Long writerId,
            String writerNameHeader,
            CreateReviewRequest request,
            List<MultipartFile> files
    ) {
        validateUser(writerId);
        if (reviewRepository.existsByProductIdAndWriterId(request.productId(), writerId)) {
            throw new ConflictException("You have already reviewed this product");
        }

        List<MultipartFile> safeFiles = files == null ? List.of() : files;
        validateFileSize(safeFiles);

        Review review = new Review();
        review.setProductId(request.productId());
        review.setWriterId(writerId);
        review.setWriterName(resolveWriterName(writerId, writerNameHeader));
        review.setStar(request.star());
        review.setPreferenceScore(request.preferenceScore());
        review.setRepurchaseScore(request.repurchaseScore());
        review.setFreshnessScore(request.freshnessScore());
        review.setContent(request.content());
        review.setLikeCount(0L);
        List<ReviewMediaPayload> uploadedMedia = uploadAndBuildMediaPayloads(safeFiles);
        review.setReviewMediaJson(uploadedMedia);

        // backward compatibility for existing clients/data migration period
        List<String> uploadedMediaUrls = uploadedMedia.stream().map(ReviewMediaPayload::getUrl).toList();
        review.setReviewMediaUrls(joinMediaKeys(uploadedMediaUrls));
        review.setReviewMediaUrl(uploadedMediaUrls.isEmpty() ? null : uploadedMediaUrls.get(0));
        review.setMediaType(resolveMediaType(safeFiles));

        Review saved = reviewRepository.save(review);

        return new ReviewCreateResponse(
                "success",
                new ReviewCreateDataDto(
                        ensurePublicId(saved).toString(),
                        "Review has been successfully created.",
                        "/profile/reviews"
                )
        );
    }

    @Transactional
    public ReviewUpdateResponse updateReview(
            Long writerId,
            UUID publicId,
            UpdateReviewRequest request,
            List<MultipartFile> files
    ) {
        validateUser(writerId);
        Review review = reviewRepository.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        ensurePublicId(review);
        validateOwner(review, writerId);

        List<MultipartFile> safeFiles = files == null ? List.of() : files;
        validateFileSize(safeFiles);

        review.setStar(request.star());
        review.setPreferenceScore(request.preferenceScore());
        review.setRepurchaseScore(request.repurchaseScore());
        review.setFreshnessScore(request.freshnessScore());
        review.setContent(request.content());

        List<ReviewMediaPayload> uploadedMedia = uploadAndBuildMediaPayloads(safeFiles);
        if (!uploadedMedia.isEmpty()) {
            review.setReviewMediaJson(uploadedMedia);

            // backward compatibility for existing clients/data migration period
            List<String> uploadedMediaUrls = uploadedMedia.stream().map(ReviewMediaPayload::getUrl).toList();
            review.setReviewMediaUrls(joinMediaKeys(uploadedMediaUrls));
            review.setReviewMediaUrl(uploadedMediaUrls.get(0));
            review.setMediaType(resolveMediaType(safeFiles));
        }

        return new ReviewUpdateResponse("success", toDetailDto(review));
    }

    @Transactional
    public ReviewDeleteResponse deleteReview(Long writerId, UUID publicId) {
        validateUser(writerId);

        Review review = reviewRepository.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        ensurePublicId(review);
        validateOwner(review, writerId);

        review.markDeleted(writerId, DEFAULT_DELETE_REASON);
        return new ReviewDeleteResponse("success", "Review has been deleted.");
    }

    private void validateUser(Long writerId) {
        if (writerId == null || writerId <= 0) {
            throw new UnauthorizedException("X-User-Id header is required");
        }
    }

    private void validateOwner(Review review, Long writerId) {
        if (!review.getWriterId().equals(writerId)) {
            throw new UnauthorizedException("Invalid token");
        }
    }

    private void validateFileSize(List<MultipartFile> files) {
        int actualFileCount = 0;
        int videoCount = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            actualFileCount++;
            if (actualFileCount > MAX_FILE_COUNT) {
                throw new IllegalArgumentException("A maximum of 5 files can be uploaded.");
            }

            if (file.getSize() > MAX_FILE_BYTES) {
                throw new PayloadTooLargeException("File size exceeds limit (Max 50MB)");
            }

            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("video/")) {
                videoCount++;
                if (videoCount > MAX_VIDEO_COUNT) {
                    throw new IllegalArgumentException("Only one video can be uploaded.");
                }
            }
        }
    }

    private List<ReviewMediaPayload> uploadAndBuildMediaPayloads(List<MultipartFile> files) {
        List<ReviewMediaPayload> uploadedMedia = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String uploadedKey = s3Component.upload(file, S3Directory.REVIEW);
                String url = s3Component.toPublicUrl(uploadedKey);
                uploadedMedia.add(new ReviewMediaPayload(url, mediaTypeFromContentType(file.getContentType())));
            }
        }
        return uploadedMedia;
    }

    private ReviewDetailDto toDetailDto(Review review) {
        UUID publicId = ensurePublicId(review);
        List<String> mediaUrls = resolvePublicMediaUrls(review);
        List<ReviewMediaDto> mediaDtos = toMediaDtos(mediaUrls, review.getReviewMediaJson());
        return new ReviewDetailDto(
                publicId.toString(),
                mediaDtos,
                review.getMediaType(),
                review.getLikeCount(),
                review.getWriterName(),
                review.getStar(),
                safeInt(review.getPreferenceScore()),
                safeInt(review.getRepurchaseScore()),
                safeInt(review.getFreshnessScore()),
                review.getContent(),
                review.getCreatedAt(),
                REVIEW_API_PATH + "/" + publicId + "/report"
        );
    }

    private UUID ensurePublicId(Review review) {
        if (review.getPublicId() != null) {
            return review.getPublicId();
        }
        UUID generated = UuidCreator.getTimeOrderedEpoch();
        review.setPublicId(generated);
        return generated;
    }

    private String resolveMediaType(List<MultipartFile> files) {
        boolean hasVideo = false;
        boolean hasImage = false;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("video/")) {
                hasVideo = true;
            } else {
                hasImage = true;
            }
        }

        // 필터 우선순위: VIDEO > IMAGE > TEXT
        if (hasVideo) {
            return "VIDEO";
        }
        if (hasImage) {
            return "IMAGE";
        }
        return "TEXT";
    }

    private String mediaTypeFromContentType(String contentType) {
        if (contentType != null && contentType.startsWith("video/")) {
            return "VIDEO";
        }
        return "IMAGE";
    }

    private List<String> resolvePublicMediaUrls(Review review) {
        if (review.getReviewMediaJson() != null && !review.getReviewMediaJson().isEmpty()) {
            return review.getReviewMediaJson().stream()
                    .map(ReviewMediaPayload::getUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .toList();
        }

        List<String> keys = splitMediaKeys(review.getReviewMediaUrls());
        if (keys.isEmpty() && review.getReviewMediaUrl() != null && !review.getReviewMediaUrl().isBlank()) {
            keys = List.of(review.getReviewMediaUrl());
        }

        return keys.stream()
                .map(s3Component::toPublicUrl)
                .toList();
    }

    private String joinMediaKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        return String.join(MEDIA_URL_DELIMITER, keys);
    }

    private List<String> splitMediaKeys(String joinedKeys) {
        if (joinedKeys == null || joinedKeys.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(joinedKeys.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private List<ReviewMediaDto> toMediaDtos(List<String> mediaUrls, List<ReviewMediaPayload> mediaPayloads) {
        if (mediaPayloads != null && !mediaPayloads.isEmpty()) {
            return mediaPayloads.stream()
                    .filter(media -> media.getUrl() != null && !media.getUrl().isBlank())
                    .map(media -> new ReviewMediaDto(media.getUrl(), normalizeMediaType(media.getMediaType())))
                    .toList();
        }

        return mediaUrls.stream()
                .map(url -> new ReviewMediaDto(url, detectMediaTypeFromUrl(url)))
                .toList();
    }

    private String normalizeMediaType(String mediaType) {
        if ("VIDEO".equalsIgnoreCase(mediaType)) {
            return "VIDEO";
        }
        return "IMAGE";
    }

    private String detectMediaTypeFromUrl(String url) {
        if (url == null) {
            return "IMAGE";
        }

        String lower = url.toLowerCase();
        if (lower.endsWith(".mp4")
                || lower.endsWith(".mov")
                || lower.endsWith(".avi")
                || lower.endsWith(".wmv")
                || lower.endsWith(".mkv")
                || lower.endsWith(".webm")
                || lower.endsWith(".m4v")) {
            return "VIDEO";
        }
        return "IMAGE";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String resolveWriterName(Long writerId, String writerNameHeader) {
        if (StringUtils.hasText(writerNameHeader)) {
            return writerNameHeader.trim();
        }
        return "user-" + writerId;
    }
}
