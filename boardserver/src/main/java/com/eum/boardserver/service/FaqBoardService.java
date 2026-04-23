package com.eum.boardserver.service;

import com.eum.boardserver.dto.request.FaqCreateRequest;
import com.eum.boardserver.dto.response.FaqDetailResponse;
import com.eum.boardserver.entity.Faq;
import com.eum.boardserver.repository.FaqRepository;
import com.eum.s3.S3Component;
import com.eum.s3.S3Directory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FaqBoardService {

    private final FaqRepository faqRepository;
    private final S3Component s3Component;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Transactional
    public Long createFaq(FaqCreateRequest request) {
        Faq faq = Faq.builder()
                .title(request.title())
                .content(request.content())
                .author(request.author())
                .category(request.category())
                .isPinned(request.isPinned())
                .contentImageUrls(normalizeImageUrls(request.contentImageUrls()))
                .actions(normalizeActions(request.actions()))
                .build();

        return faqRepository.save(faq).getId();
    }

    @Transactional
    public Long createFaqWithImages(FaqCreateRequest request, List<MultipartFile> images) {
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

        Faq faq = Faq.builder()
                .title(request.title())
                .content(request.content())
                .author(request.author())
                .category(request.category())
                .isPinned(request.isPinned())
                .contentImageUrls(mergedImageUrls)
                .actions(normalizeActions(request.actions()))
                .build();

        return faqRepository.save(faq).getId();
    }

    @Transactional(readOnly = true)
    public FaqDetailResponse getFaqDetail(Long faqId) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Faq not found: " + faqId));

        return FaqDetailResponse.builder()
                .id(faq.getId())
                .category(faq.getCategory())
                .title(faq.getTitle())
                .content(faq.getContent())
                .author(faq.getAuthor())
                .viewCount(faq.getViewCount())
                .isPinned(faq.getIsPinned())
                .contentImageUrls(faq.getContentImageUrls() == null ? List.of() : faq.getContentImageUrls())
                .actions(toClientActions(faq.getActions()))
                .createdAt(faq.getCreatedAt() == null ? null : faq.getCreatedAt().format(DATE_TIME_FORMATTER))
                .updatedAt(faq.getUpdatedAt() == null ? null : faq.getUpdatedAt().format(DATE_TIME_FORMATTER))
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
