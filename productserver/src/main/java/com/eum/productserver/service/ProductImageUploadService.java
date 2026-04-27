package com.eum.productserver.service;

import com.eum.productserver.dto.request.item.save.ImageFileSaveDto;
import com.eum.s3.S3Component;
import com.eum.s3.S3Directory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductImageUploadService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    private final S3Component s3Component;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    /**
     * multipart로 받은 상품 이미지를 S3에 업로드하고, DB에 저장할 URL/Key DTO로 변환합니다.
     */
    public List<ImageFileSaveDto> uploadAll(List<MultipartFile> files) {
        List<MultipartFile> actualFiles = filterActualFiles(files);
        validateFiles(actualFiles);

        List<ImageFileSaveDto> uploadedImages = new ArrayList<>();
        for (MultipartFile file : actualFiles) {
            uploadedImages.add(uploadValidated(file));
        }
        return uploadedImages;
    }

    public ImageFileSaveDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        validateFiles(List.of(file));
        return uploadValidated(file);
    }

    public List<MultipartFile> filterActualFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }

    private void validateFiles(List<MultipartFile> files) {
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("상품 이미지는 파일당 10MB 이하여야 합니다.");
            }

            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
                throw new IllegalArgumentException("상품 이미지는 jpg, png, gif, webp 형식만 등록할 수 있습니다.");
            }
        }
    }

    private ImageFileSaveDto uploadValidated(MultipartFile file) {
        String imageKey = s3Component.upload(file, S3Directory.PRODUCT);
        ImageFileSaveDto image = new ImageFileSaveDto();
        image.setImageKey(imageKey);
        image.setImageUrl(buildS3Url(imageKey));
        return image;
    }

    private String buildS3Url(String imageKey) {
        String encodedKey = URLEncoder.encode(imageKey, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, encodedKey);
    }
}
