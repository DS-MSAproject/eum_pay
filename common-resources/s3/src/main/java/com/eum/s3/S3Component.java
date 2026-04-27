package com.eum.s3;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
public class S3Component {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static:ap-northeast-2}")
    private String region;

    @Value("${cloud.aws.cdn.enabled:${CDN_ENABLED:false}}")
    private boolean cdnEnabled;

    @Value("${cloud.aws.cdn.domain:${CDN_DOMAIN:}}")
    private String cdnDomain;

    public String upload(MultipartFile file, S3Directory directory) {
        String fileName = directory.getPath() + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );

            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("S3 upload failed", e);
        }
    }

    public String toPublicUrl(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            return null;
        }

        String trimmed = keyOrUrl.trim();
        String key = resolveKey(trimmed);
        if (key == null || key.isBlank()) {
            return trimmed;
        }

        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");

        if (cdnEnabled && cdnDomain != null && !cdnDomain.isBlank()) {
            return stripTrailingSlash(cdnDomain) + "/" + encodedKey;
        }

        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, encodedKey);
    }

    private String resolveKey(String keyOrUrl) {
        String lower = keyOrUrl.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return keyOrUrl;
        }

        URI uri;
        try {
            uri = URI.create(keyOrUrl);
        } catch (IllegalArgumentException e) {
            return null;
        }

        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null || path == null || path.isBlank()) {
            return null;
        }

        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        if (normalizedPath.isBlank()) {
            return null;
        }

        if (isCdnHost(host) || isBucketVirtualHost(host)) {
            return normalizedPath;
        }

        if (isPathStyleHost(host)) {
            String bucketPrefix = bucket + "/";
            if (normalizedPath.startsWith(bucketPrefix)) {
                return normalizedPath.substring(bucketPrefix.length());
            }
        }

        return null;
    }

    private boolean isBucketVirtualHost(String host) {
        String lowerHost = host.toLowerCase(Locale.ROOT);
        String bucketLower = bucket.toLowerCase(Locale.ROOT);
        String regionLower = region.toLowerCase(Locale.ROOT);
        return Objects.equals(lowerHost, bucketLower + ".s3." + regionLower + ".amazonaws.com")
                || Objects.equals(lowerHost, bucketLower + ".s3.amazonaws.com");
    }

    private boolean isPathStyleHost(String host) {
        String lowerHost = host.toLowerCase(Locale.ROOT);
        String regionLower = region.toLowerCase(Locale.ROOT);
        return Objects.equals(lowerHost, "s3." + regionLower + ".amazonaws.com")
                || Objects.equals(lowerHost, "s3.amazonaws.com");
    }

    private boolean isCdnHost(String host) {
        if (cdnDomain == null || cdnDomain.isBlank() || host == null || host.isBlank()) {
            return false;
        }

        String normalizedDomain = cdnDomain.trim();
        if (!normalizedDomain.startsWith("http://") && !normalizedDomain.startsWith("https://")) {
            normalizedDomain = "https://" + normalizedDomain;
        }

        try {
            URI uri = URI.create(normalizedDomain);
            String cdnHost = uri.getHost();
            return cdnHost != null && cdnHost.equalsIgnoreCase(host);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
