package com.eum.productserver.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ProductImageUrlService {

    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${product.image.presigned-url.enabled:true}")
    private boolean presignedUrlEnabled;

    @Value("${product.image.presigned-url.expiration-minutes:30}")
    private long expirationMinutes;

    public String toDisplayUrl(String imageUrlOrKey) {
        if (imageUrlOrKey == null || imageUrlOrKey.isBlank()) {
            return imageUrlOrKey;
        }

        String imageKey = extractS3Key(imageUrlOrKey);
        if (!presignedUrlEnabled || imageKey == null || imageKey.isBlank()) {
            return imageUrlOrKey;
        }

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(imageKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(Math.max(expirationMinutes, 1)))
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (RuntimeException ex) {
            return imageUrlOrKey;
        }
    }

    private String extractS3Key(String imageUrlOrKey) {
        if (!imageUrlOrKey.startsWith("http://") && !imageUrlOrKey.startsWith("https://")) {
            return imageUrlOrKey;
        }

        try {
            URI uri = URI.create(imageUrlOrKey);
            String host = uri.getHost();
            if (host == null) {
                return null;
            }

            String path = stripLeadingSlash(uri.getPath());
            if (host.equals(bucket + ".s3.amazonaws.com") || host.startsWith(bucket + ".s3.")) {
                return decode(path);
            }

            if (host.startsWith("s3.") && path.startsWith(bucket + "/")) {
                return decode(path.substring(bucket.length() + 1));
            }

            return null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String stripLeadingSlash(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
