package com.eum.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3Component {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static:ap-northeast-2}")
    private String region;

    public String upload(MultipartFile file, S3Directory directory) {
        // 💡 [무결성 명명 규칙] 디렉토리/UUID_파일명 구조로 중복 방지
        String fileName = directory.getPath() + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("S3 업로드 실패: 오류", e);
        }
    }

    public String toPublicUrl(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            return null;
        }
        String trimmed = keyOrUrl.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed;
        }
        String encodedKey = URLEncoder.encode(trimmed, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, encodedKey);
    }
}
