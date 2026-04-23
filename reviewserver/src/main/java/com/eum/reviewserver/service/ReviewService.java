package com.eum.reviewserver.service;

import com.eum.reviewserver.dto.request.CreateReviewRequest;
import com.eum.reviewserver.dto.request.UpdateReviewRequest;
import com.eum.reviewserver.dto.response.PageInfoDto;
import com.eum.reviewserver.dto.response.ReviewBodyDto;
import com.eum.reviewserver.dto.response.ReviewCreateDataDto;
import com.eum.reviewserver.dto.response.ReviewCreateResponse;
import com.eum.reviewserver.dto.response.ReviewDeleteResponse;
import com.eum.reviewserver.dto.response.ReviewDetailDto;
import com.eum.reviewserver.dto.response.ReviewDetailResponse;
import com.eum.reviewserver.dto.response.ReviewHeaderDto;
import com.eum.reviewserver.dto.response.ReviewListResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public ReviewListResponse getReviews(
            String authorization,
            Long productId,
            String keyword,
            String sortType,
            String reviewType,
            Integer page,
            Integer size
    ) {
        validateToken(authorization);
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null ? 5 : Math.max(size, 1);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviewPage = reviewRepository.findPageByCondition(productId, keyword, sortType, reviewType, pageable);
        List<Review> allByProduct = reviewRepository.findAllByProductId(productId);

        List<ReviewBodyDto> body = reviewPage.getContent().stream()
                .map(review -> {
                    List<String> mediaUrls = resolvePublicMediaUrls(review);
                    List<ReviewMediaDto> mediaDtos = toMediaDtos(mediaUrls, review.getReviewMediaJson());
                    return new ReviewBodyDto(
                            review.getId(),
                            mediaDtos,
                            review.getLikeCount(),
                            review.getWriterName(),
                            review.getCreatedAt(),
                            review.getContent(),
                            review.getStar(),
                            review.getMediaType(),
                            REVIEW_API_PATH + "/" + review.getId()
                    );
                })
                .toList();

        return new ReviewListResponse(
                "success",
                buildHeader(allByProduct),
                body,
                PageInfoDto.from(reviewPage)
        );
    }

    @Transactional
    public ReviewDetailResponse getReviewDetail(String authorization, Long reviewId, Boolean isInterested) {
        validateToken(authorization);
        Review review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        if (Boolean.TRUE.equals(isInterested)) {
            review.setLikeCount(review.getLikeCount() + 1);
        }

        return new ReviewDetailResponse("success", toDetailDto(review));
    }

    @Transactional
    public ReviewCreateResponse createReview(
            String authorization,
            Long writerId,
            CreateReviewRequest request,
            List<MultipartFile> files
    ) {
        validateToken(authorization);
        validateUser(writerId);
        if (reviewRepository.existsByProductIdAndWriterId(request.productId(), writerId)) {
            throw new ConflictException("You have already reviewed this product");
        }

        List<MultipartFile> safeFiles = files == null ? List.of() : files;
        validateFileSize(safeFiles);

        Review review = new Review();
        review.setProductId(request.productId());
        review.setWriterId(writerId);
        review.setWriterName(resolveWriterName(writerId));
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
                        saved.getId(),
                        "Review has been successfully created.",
                        "/profile/reviews"
                )
        );
    }

    @Transactional
    public ReviewUpdateResponse updateReview(
            String authorization,
            Long writerId,
            Long reviewId,
            UpdateReviewRequest request,
            List<MultipartFile> files
    ) {
        validateToken(authorization);
        validateUser(writerId);
        Review review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
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
    public ReviewDeleteResponse deleteReview(String authorization, Long writerId, Long reviewId) {
        validateToken(authorization);
        validateUser(writerId);

        Review review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        validateOwner(review, writerId);

        review.markDeleted(writerId, DEFAULT_DELETE_REASON);
        return new ReviewDeleteResponse("success", "Review has been deleted.");
    }

    private void validateToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid token");
        }
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
        List<String> mediaUrls = resolvePublicMediaUrls(review);
        List<ReviewMediaDto> mediaDtos = toMediaDtos(mediaUrls, review.getReviewMediaJson());
        return new ReviewDetailDto(
                review.getId(),
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
                REVIEW_API_PATH + "/" + review.getId() + "/report"
        );
    }

    private ReviewHeaderDto buildHeader(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return new ReviewHeaderDto(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        long total = reviews.size();
        long star5 = reviews.stream().filter(r -> r.getStar() == 5).count();
        long star4 = reviews.stream().filter(r -> r.getStar() == 4).count();
        long star3 = reviews.stream().filter(r -> r.getStar() == 3).count();
        long star2 = reviews.stream().filter(r -> r.getStar() == 2).count();
        long star1 = reviews.stream().filter(r -> r.getStar() == 1).count();
        long preferenceCount = reviews.stream().filter(r -> safeInt(r.getPreferenceScore()) >= 4).count();
        long repurchaseCount = reviews.stream().filter(r -> safeInt(r.getRepurchaseScore()) >= 4).count();
        long freshnessCount = reviews.stream().filter(r -> safeInt(r.getFreshnessScore()) >= 4).count();
        double starAverage = reviews.stream().mapToInt(Review::getStar).average().orElse(0.0);

        return new ReviewHeaderDto(
                round(starAverage),
                total,
                ratio(star5, total),
                ratio(star4, total),
                ratio(star3, total),
                ratio(star2, total),
                ratio(star1, total),
                ratio(preferenceCount, total),
                ratio(repurchaseCount, total),
                ratio(freshnessCount, total)
        );
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

    private List<ReviewMediaDto> toMediaDtos(List<String> mediaUrls) {
        return toMediaDtos(mediaUrls, null);
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

    private double ratio(long value, long total) {
        if (total == 0) {
            return 0.0;
        }
        return round((value * 100.0) / total);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String resolveWriterName(Long writerId) {
        return "user-" + writerId;
    }
}
