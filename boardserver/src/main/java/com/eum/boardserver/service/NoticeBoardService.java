package com.eum.boardserver.service;

import com.eum.boardserver.dto.request.NoticeCreateRequest;
import com.eum.boardserver.dto.response.NoticeDetailResponse;
import com.eum.boardserver.entity.Notice;
import com.eum.boardserver.repository.NoticeRepository;
import com.eum.s3.S3Component;
import com.eum.s3.S3Directory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NoticeBoardService {
    private final NoticeRepository noticeRepository;
    private final S3Component s3Component;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Transactional
    public Long createNotice(NoticeCreateRequest request) {
        List<String> normalizedImageUrls = normalizeImageUrls(request.contentImageUrls());

        Notice notice = Notice.builder()
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .isPinned(request.isPinned())
                .contentImageUrls(normalizedImageUrls)
                .actions(normalizeActions(request.actions()))
                .build();

        return noticeRepository.save(notice).getId();
    }

    @Transactional
    public Long createNoticeWithImages(NoticeCreateRequest request, List<MultipartFile> images) {
        List<String> mergedImageUrls = new ArrayList<>();

        mergedImageUrls.addAll(normalizeImageUrls(request.contentImageUrls()));

        if (images != null) {
            List<String> uploadedUrls = images.stream()
                    .filter(Objects::nonNull)
                    .filter(file -> !file.isEmpty())
                    .map(file -> s3Component.toPublicUrl(s3Component.upload(file, S3Directory.BOARD)))
                    .toList();
            mergedImageUrls.addAll(uploadedUrls);
        }

        Notice notice = Notice.builder()
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .isPinned(request.isPinned())
                .contentImageUrls(mergedImageUrls)
                .actions(normalizeActions(request.actions()))
                .build();

        return noticeRepository.save(notice).getId();
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse getNoticeDetail(Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found: " + noticeId));

        return NoticeDetailResponse.builder()
                .id(notice.getId())
                .category(notice.getCategory())
                .title(notice.getTitle())
                .content(notice.getContent())
                .isPinned(notice.getIsPinned())
                .contentImageUrls(notice.getContentImageUrls() == null ? List.of() : notice.getContentImageUrls())
                .actions(toClientActions(notice.getActions()))
                .createdAt(notice.getCreatedAt() == null ? null : notice.getCreatedAt().format(DATE_TIME_FORMATTER))
                .updatedAt(notice.getUpdatedAt() == null ? null : notice.getUpdatedAt().format(DATE_TIME_FORMATTER))
                .build();
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }

        return imageUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .map(s3Component::toPublicUrl)
                .toList();
    }

    private List<Map<String, Object>> normalizeActions(List<Map<String, Object>> actions) {
        if (actions == null) {
            return List.of();
        }

        return actions.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeAction)
                .toList();
    }

    private Map<String, Object> normalizeAction(Map<String, Object> rawAction) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        rawAction.forEach((key, value) -> normalized.put(normalizeActionKey(key), value));
        return normalized;
    }

    private List<Map<String, Object>> toClientActions(List<Map<String, Object>> actions) {
        if (actions == null) {
            return List.of();
        }

        return actions.stream()
                .filter(Objects::nonNull)
                .map(this::toClientAction)
                .toList();
    }

    private Map<String, Object> toClientAction(Map<String, Object> storedAction) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        storedAction.forEach((key, value) -> mapped.put(toClientActionKey(key), value));
        return mapped;
    }

    private String normalizeActionKey(String key) {
        return switch (key) {
            case "targetUrl", "target_url" -> "target_url";
            case "actionType", "action_type" -> "action_type";
            case "sortOrder", "sort_order" -> "sort_order";
            default -> key;
        };
    }

    private String toClientActionKey(String key) {
        return switch (key) {
            case "target_url", "targetUrl" -> "targetUrl";
            case "action_type", "actionType" -> "actionType";
            case "sort_order", "sortOrder" -> "sortOrder";
            default -> key;
        };
    }
}
