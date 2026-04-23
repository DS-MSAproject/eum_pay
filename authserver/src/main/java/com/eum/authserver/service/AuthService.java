package com.eum.authserver.service;

import com.eum.authserver.dto.SignupRequest;
import com.eum.authserver.dto.TokenPair;
import com.eum.authserver.entity.LoginHistory;
import com.eum.authserver.entity.Role;
import com.eum.authserver.entity.User;
import com.eum.authserver.exception.AccountLockedException;
import com.eum.authserver.jwt.JwtProvider;
import com.eum.authserver.repository.LoginAttemptRepository;
import com.eum.authserver.repository.LoginHistoryRepository;
import com.eum.authserver.repository.RefreshTokenRepository;
import com.eum.authserver.repository.UserRepository;
import com.eum.authserver.repository.ProfileRepository;
import com.eum.authserver.entity.Profile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository          userRepository;
    private final PasswordEncoder         passwordEncoder;
    private final JwtProvider             jwtProvider;
    private final RefreshTokenRepository  refreshTokenRepo;
    private final LoginAttemptRepository  loginAttemptRepo;
    private final LoginHistoryRepository  loginHistoryRepo;
    private final StringRedisTemplate     redisTemplate;
    private final ProfileRepository       profileRepository;
    private final TermsService            termsService;  // ← 신규 주입
    private final EmailVerificationService emailVerificationService;

    // ── 회원가입 ──────────────────────────────────────
    @Transactional
    public TokenPair signup(SignupRequest request, String clientIp, String userAgent) {
        if (userRepository.existsByUsername(request.getUsername()))
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");

        if (userRepository.existsByEmail(request.getEmail()))
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber()))
            throw new IllegalArgumentException("이미 등록된 전화번호입니다.");

        if (!emailVerificationService.isEmailVerifiedForSignup(request.getEmail())) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
        }

        // ← 약관 동의 검증
        if (request.getTermsAgreed() == null || request.getTermsAgreed().isEmpty()) {
            throw new IllegalArgumentException("약관 동의 정보가 없습니다.");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setProvider("local");
        user.setEmailVerified(true);

        User saved = userRepository.save(user);
        emailVerificationService.clearSignupVerification(saved.getEmail());

        // Profile 동시 생성 (1:1)
        if (!profileRepository.existsByUserId(saved.getId())) {
            profileRepository.save(Profile.createDefault(saved.getId()));
        }

        // ← 약관 동의 저장 (법적 증거)
        termsService.saveUserTermsAgreement(
                saved.getId(),
                request.getTermsAgreed(),
                clientIp,
                userAgent
        );

        log.info("회원가입 완료: username={}, email={}, terms saved",
                saved.getUsername(), saved.getEmail());

        return issueWithHistory(saved, clientIp, userAgent, "local");
    }

    // ── 아이디 로그인 ─────────────────────────────────
    @Transactional
    public TokenPair login(String username, String password,
                           String clientIp, String userAgent) {
        // 1. 계정 잠금 확인
        if (loginAttemptRepo.isLocked(username)) {
            long remain = loginAttemptRepo.getLockRemainSeconds(username);
            throw new AccountLockedException(remain);
        }

        // 2. 유저 조회
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    loginAttemptRepo.incrementFailCount(username);
                    return new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
                });

        // 3. 비밀번호 검증
        if (!passwordEncoder.matches(password, user.getPassword())) {
            long failCount = loginAttemptRepo.incrementFailCount(username);
            long remaining = 5 - failCount;

            if (remaining <= 0) {
                long lockRemain = loginAttemptRepo.getLockRemainSeconds(username);
                throw new AccountLockedException(lockRemain);
            }

            throw new IllegalArgumentException(
                    String.format("아이디 또는 비밀번호가 올바르지 않습니다. (남은 시도 횟수: %d회)", remaining)
            );
        }

        // 4. 이메일 인증 확인 (로컬 유저만)
        if ("local".equals(user.getProvider()) && !user.isEmailVerified()) {
            throw new IllegalArgumentException("이메일 인증이 필요합니다. 인증 메일을 확인해주세요.");
        }

        // 5. 로그인 성공 → 실패 기록 초기화
        loginAttemptRepo.clearFailCount(username);

        // 6. 로그인 이력 저장
        saveLoginHistory(user, clientIp, userAgent, "local");

        return issue(user);
    }

    // ── 토큰 발급 (로그인 + OAuth2 공통) ─────────────
    @Transactional
    public TokenPair issue(User user) {
        String accessToken  = jwtProvider.createAccessToken(
                user.getId(), user.getEmail(), user.getRole().getKey());
        String refreshToken = jwtProvider.createRefreshToken();
        refreshTokenRepo.save(user.getId(), refreshToken);
        return new TokenPair(accessToken, refreshToken);
    }

    // ── 소셜 로그인 토큰 발급 (이력 포함) ─────────────
    @Transactional
    public TokenPair issueWithHistory(User user, String clientIp,
                                      String userAgent, String provider) {
        saveLoginHistory(user, clientIp, userAgent, provider);
        return issue(user);
    }

    // ── Refresh Token으로 갱신 (탈취 감지 포함) ─────
    @Transactional
    public TokenPair refreshByToken(String refreshToken) {
        Long userId = refreshTokenRepo.findUserIdByToken(refreshToken)
                .orElse(null);

        if (userId == null) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }

        // 탈취 감지 — 이미 rotation된 이전 토큰으로 재시도하는 경우
        if (refreshTokenRepo.isStolenToken(userId, refreshToken)) {
            refreshTokenRepo.delete(userId);
            log.warn("[보안경고] Refresh Token 탈취 의심 — userId: {}, 모든 세션 강제 종료", userId);
            throw new IllegalArgumentException("비정상적인 토큰 사용이 감지되었습니다. 다시 로그인해주세요.");
        }

        return refresh(userId, refreshToken);
    }

    // ── Silent Refresh ────────────────────────────────
    @Transactional
    public TokenPair refresh(Long userId, String refreshToken) {
        String stored = refreshTokenRepo.find(userId);
        if (stored == null)
            throw new IllegalArgumentException("로그아웃된 사용자입니다.");

        if (!stored.equals(refreshToken))
            throw new IllegalArgumentException("Refresh Token이 일치하지 않습니다.");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return issue(user);
    }

    // ── 로그아웃 ──────────────────────────────────────
    public void logoutByToken(String accessToken) {
        if (accessToken == null || !jwtProvider.validateToken(accessToken)) return;

        String userIdStr = jwtProvider.getUserId(accessToken);
        if (userIdStr != null) {
            refreshTokenRepo.delete(Long.parseLong(userIdStr));
        }

        long remainMs = jwtProvider.getExpiration(accessToken).getTime()
                - System.currentTimeMillis();
        if (remainMs > 0) {
            redisTemplate.opsForValue().set(
                    "blacklist:" + jwtProvider.getJti(accessToken),
                    "1",
                    remainMs, TimeUnit.MILLISECONDS
            );
        }
    }

    // ── 로그인 이력 저장 ──────────────────────────────
    // 이력 저장 실패해도 로그인 자체는 성공 처리
    @Transactional
    public void saveLoginHistory(User user, String clientIp,
                                 String userAgent, String provider) {
        try {
            LoginHistory history = new LoginHistory();
            history.setUser(user);
            history.setClientIp(clientIp != null ? clientIp : "unknown");
            history.setUserAgent(userAgent != null ? userAgent : "unknown");
            history.setProvider(provider);
            loginHistoryRepo.save(history);
            log.info("로그인 이력 저장: userId={}, provider={}, ip={}",
                    user.getId(), provider, clientIp);
        } catch (Exception e) {
            log.warn("로그인 이력 저장 실패 (무시): {}", e.getMessage());
        }
    }
}
