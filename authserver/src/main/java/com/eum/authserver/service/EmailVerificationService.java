package com.eum.authserver.service;

import com.eum.authserver.entity.User;
import com.eum.authserver.repository.EmailVerificationRepository;
import com.eum.authserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    // required = false — 메일 설정이 없어도 서버 기동 가능
    // 메일 설정이 없으면 콘솔 로그로 코드 출력 (개발 환경용)
    private final JavaMailSender               mailSender;
    private final EmailVerificationRepository  verifyRepo;
    private final UserRepository               userRepository;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── 인증 코드 발송 ────────────────────────────────
    public void sendVerificationCode(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        verifyRepo.save(email, code);

        if (mailUsername == null || mailUsername.isBlank()) {
            // 메일 설정 없음 → 개발 환경: 콘솔에 코드 출력
            log.info("====================================================");
            log.info("[개발 환경] 이메일 인증 코드: {} → 수신: {}", code, email);
            log.info("====================================================");
        } else {
            // 운영 환경: 실제 이메일 발송
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(email);
                message.setSubject("[DSEUM] 이메일 인증 코드");
                message.setText(buildEmailBody(code));
                mailSender.send(message);
                log.info("인증 코드 발송 완료: {}", email);
            } catch (Exception e) {
                log.error("이메일 발송 실패: {}", e.getMessage());
                verifyRepo.delete(email);
                throw new IllegalStateException("이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
            }
        }
    }

    // ── 인증 코드 검증 ────────────────────────────────
    @Transactional
    public void verifyCode(String email, String code) {
        String stored = verifyRepo.find(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "인증 코드가 만료되었거나 존재하지 않습니다. 다시 발송해주세요."));

        if (!stored.equals(code))
            throw new IllegalArgumentException("인증 코드가 올바르지 않습니다.");

        verifyRepo.delete(email);
        verifyRepo.markVerified(email);

        log.info("이메일 인증 완료: {}", email);
    }

    public boolean isEmailVerifiedForSignup(String email) {
        return verifyRepo.isVerified(email);
    }

    public void clearSignupVerification(String email) {
        verifyRepo.clearVerified(email);
    }

    private String buildEmailBody(String code) {
        return String.join("\n",
                "안녕하세요! DSEUM입니다.",
                "",
                "인증 코드: " + code,
                "",
                "인증 코드는 10분간 유효합니다.",
                "본인이 요청하지 않은 경우 이 메일을 무시해주세요."
        );
    }
}
