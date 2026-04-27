package com.eum.authserver.dto;

import com.eum.authserver.entity.Terms;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TermsResponse {

    private String status;
    private List<TermItem> terms;

    @Getter
    @Builder
    public static class TermItem {
        private String id;              // termId
        private String title;           // 약관 제목
        private String content;         // 약관 본문 (HTML)
        private boolean isRequired;     // 필수 여부
        private String version;         // 약관 버전
        private LocalDateTime lastUpdated;  // 최종 수정일

        public static TermItem from(Terms terms) {
            return TermItem.builder()
                    .id(terms.getTermId())
                    .title(terms.getTitle())
                    .content(terms.getContent())
                    .isRequired(terms.isRequired())
                    .version(terms.getVersion())
                    .lastUpdated(terms.getUpdatedAt())
                    .build();
        }
    }

    public static TermsResponse success(List<Terms> termsList) {
        return TermsResponse.builder()
                .status("success")
                .terms(termsList.stream()
                        .map(TermItem::from)
                        .toList())
                .build();
    }
}