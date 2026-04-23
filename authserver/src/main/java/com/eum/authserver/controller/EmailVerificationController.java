package com.eum.authserver.controller;

import com.eum.authserver.service.EmailVerificationService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    // ── 인증 코드 발송 ────────────────────────────────
    // 회원가입 완료 후 프론트에서 이 API 호출
    @PostMapping("/send")
    public ResponseEntity<Void> sendCode(
            @RequestParam @NotBlank @Email String email) {

        emailVerificationService.sendVerificationCode(email);
        return ResponseEntity.ok().build();
    }

    // ── 인증 코드 검증 ────────────────────────────────
    @PostMapping("/verify")
    public ResponseEntity<Void> verifyCode(
            @RequestParam @NotBlank @Email String email,
            @RequestParam @NotBlank @Size(min = 6, max = 6) String code) {

        emailVerificationService.verifyCode(email, code);
        return ResponseEntity.ok().build();
    }
}
