package com.eum.boardserver.controller;

import com.eum.s3.S3Component;
import com.eum.s3.S3Directory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/board/test")
@RequiredArgsConstructor
public class BoardS3TestController {

    // 💡 [무결성 포인트] 공통 모듈에서 깎은 엔진을 주입받습니다.
    private final S3Component s3Component;

    @PostMapping("/upload")
    public ResponseEntity<String> testUpload(@RequestPart("file") MultipartFile file) {
        log.info("S3 업로드 테스트 시작 - 파일명: {}", file.getOriginalFilename());

        try {
            String savedPath = s3Component.upload(file, S3Directory.BOARD);

            log.info("S3 업로드 성공 - 저장 경로: {}", savedPath);
            return ResponseEntity.ok("성공! 경로: " + savedPath);

        } catch (Exception e) {
            log.error("S3 업로드 중 오류 발생", e);
            return ResponseEntity.internalServerError().body("실패: " + e.getMessage());
        }
    }
}
