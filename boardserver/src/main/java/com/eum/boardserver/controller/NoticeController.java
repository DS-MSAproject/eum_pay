package com.eum.boardserver.controller;

import com.eum.boardserver.dto.request.NoticeCreateRequest;
import com.eum.boardserver.dto.response.NoticeDetailResponse;
import com.eum.boardserver.service.NoticeBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeBoardService noticeService;

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Long> saveNotice(@RequestBody NoticeCreateRequest notice) {
        return ResponseEntity.ok(noticeService.createNotice(notice));
    }

    @GetMapping("/{noticeId}")
    public ResponseEntity<NoticeDetailResponse> getNoticeDetail(@PathVariable Long noticeId) {
        return ResponseEntity.ok(noticeService.getNoticeDetail(noticeId));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Long> saveNoticeWithImages(
            @RequestPart("notice") NoticeCreateRequest notice,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        return ResponseEntity.ok(noticeService.createNoticeWithImages(notice, images));
    }
}
