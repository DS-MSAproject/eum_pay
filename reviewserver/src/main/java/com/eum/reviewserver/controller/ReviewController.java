package com.eum.reviewserver.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eum.reviewserver.dto.request.CreateReviewRequest;
import com.eum.reviewserver.dto.request.UpdateReviewRequest;
import com.eum.reviewserver.dto.response.ReviewCreateResponse;
import com.eum.reviewserver.dto.response.ReviewDeleteResponse;
import com.eum.reviewserver.dto.response.ReviewDetailResponse;
import com.eum.reviewserver.dto.response.ReviewListResponse;
import com.eum.reviewserver.dto.response.ReviewUpdateResponse;
import com.eum.reviewserver.service.ReviewService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/reviews")
public class    ReviewController {

    private final ReviewService reviewService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ReviewController(ReviewService reviewService, ObjectMapper objectMapper, Validator validator) {
        this.reviewService = reviewService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping
    public ResponseEntity<ReviewListResponse> getReviews(
            @RequestHeader("Authorization") String authorization,
            @RequestParam Long productId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortType,
            @RequestParam(required = false) String reviewType,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "5") Integer size
    ) {
        return ResponseEntity.ok(reviewService.getReviews(authorization, productId, keyword, sortType, reviewType, page, size));
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewDetailResponse> getReviewDetail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long reviewId,
            @RequestParam(required = false) Boolean isInterested
    ) {
        return ResponseEntity.ok(reviewService.getReviewDetail(authorization, reviewId, isInterested));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReviewCreateResponse> createReview(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-User-Id") Long userId,
            @RequestPart("data") String data,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.ok(
                reviewService.createReview(authorization, userId, parseAndValidate(data, CreateReviewRequest.class), files)
        );
    }

    @PutMapping(value = "/{reviewId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReviewUpdateResponse> updateReview(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long reviewId,
            @RequestPart("data") String data,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.ok(
                reviewService.updateReview(authorization, userId, reviewId, parseAndValidate(data, UpdateReviewRequest.class), files)
        );
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ReviewDeleteResponse> deleteReview(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long reviewId
    ) {
        return ResponseEntity.ok(reviewService.deleteReview(authorization, userId, reviewId));
    }

    private <T> T parseAndValidate(String rawData, Class<T> type) {
        final T request;
        try {
            request = objectMapper.readValue(rawData, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid review data");
        }

        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
        return request;
    }
}
