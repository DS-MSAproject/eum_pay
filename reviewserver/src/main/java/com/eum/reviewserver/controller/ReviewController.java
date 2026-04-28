package com.eum.reviewserver.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eum.reviewserver.dto.request.CreateReviewRequest;
import com.eum.reviewserver.dto.request.UpdateReviewRequest;
import com.eum.reviewserver.dto.response.ReviewCreateResponse;
import com.eum.reviewserver.dto.response.ReviewDeleteResponse;
import com.eum.reviewserver.dto.response.ReviewDetailResponse;
import com.eum.reviewserver.dto.response.ReviewUpdateResponse;
import com.eum.reviewserver.service.ReviewService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
public class ReviewController {

    private final ReviewService reviewService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ReviewController(ReviewService reviewService, ObjectMapper objectMapper, Validator validator) {
        this.reviewService = reviewService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ReviewDetailResponse> getReviewDetail(
            @PathVariable UUID publicId,
            @RequestParam(required = false) Boolean isInterested
    ) {
        return ResponseEntity.ok(reviewService.getReviewDetail(publicId, isInterested));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReviewCreateResponse> createReview(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            @RequestPart("data") String data,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.ok(
                reviewService.createReview(userId, userName, parseAndValidate(data, CreateReviewRequest.class), files)
        );
    }

    @PutMapping(value = "/{publicId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReviewUpdateResponse> updateReview(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable UUID publicId,
            @RequestPart("data") String data,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.ok(
                reviewService.updateReview(userId, publicId, parseAndValidate(data, UpdateReviewRequest.class), files)
        );
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<ReviewDeleteResponse> deleteReview(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable UUID publicId
    ) {
        return ResponseEntity.ok(reviewService.deleteReview(userId, publicId));
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
