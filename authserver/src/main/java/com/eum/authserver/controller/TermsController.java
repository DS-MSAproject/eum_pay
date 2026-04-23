package com.eum.authserver.controller;

import com.eum.authserver.dto.TermsResponse;
import com.eum.authserver.service.TermsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsService termsService;

    // ── 활성 약관 목록 조회 (회원가입/소셜 신규 가입 시)
    // GET /auth/terms
    // Response: { "status": "success", "terms": [...] }
    @GetMapping
    public ResponseEntity<TermsResponse> getActiveTerms() {
        TermsResponse response = termsService.getActiveTerms();
        return ResponseEntity.ok(response);
    }
}