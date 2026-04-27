package com.eum.boardserver.controller;

import com.eum.boardserver.dto.request.FaqCreateRequest;
import com.eum.boardserver.dto.response.FaqDetailResponse;
import com.eum.boardserver.service.FaqBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqBoardService faqBoardService;

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Long> saveFaq(@RequestBody FaqCreateRequest faqCreateRequest) {
        return ResponseEntity.ok(faqBoardService.createFaq(faqCreateRequest));
    }

    @GetMapping("/{faqId}")
    public ResponseEntity<FaqDetailResponse> getFaqDetail(@PathVariable Long faqId) {
        return ResponseEntity.ok(faqBoardService.getFaqDetail(faqId));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Long> saveFaqWithImages(
            @RequestPart("faq") FaqCreateRequest faqCreateRequest,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        return ResponseEntity.ok(faqBoardService.createFaqWithImages(faqCreateRequest, images));
    }
}
