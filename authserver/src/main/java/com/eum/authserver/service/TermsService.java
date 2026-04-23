package com.eum.authserver.service;

import com.eum.authserver.dto.TermsResponse;
import com.eum.authserver.entity.Terms;
import com.eum.authserver.entity.UserTermsAgreement;
import com.eum.authserver.repository.TermsRepository;
import com.eum.authserver.repository.UserTermsAgreementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsService {

    private final TermsRepository             termsRepository;
    private final UserTermsAgreementRepository userTermsAgreementRepository;

    // ── 활성화된 모든 약관 조회 (회원가입 시)
    public TermsResponse getActiveTerms() {
        List<Terms> terms = termsRepository.findByActiveTrue();
        log.info("활성 약관 조회: {} 개", terms.size());
        return TermsResponse.success(terms);
    }

    // ── 약관 동의 저장 (회원가입/소셜 신규 가입 시)
    @Transactional
    public void saveUserTermsAgreement(Long userId, Map<String, Boolean> termsAgreed,
                                       String ipAddress, String userAgent) {
        if (termsAgreed == null || termsAgreed.isEmpty()) {
            throw new IllegalArgumentException("약관 동의 정보가 없습니다.");
        }

        List<Terms> activeTerms = termsRepository.findByActiveTrue();

        if (activeTerms.isEmpty()) {
            throw new IllegalArgumentException("활성화된 약관이 존재하지 않습니다.");
        }

        // 활성 필수 약관은 key가 아예 누락된 경우도 실패 처리
        for (Terms term : activeTerms) {
            if (term.isRequired() && !Boolean.TRUE.equals(termsAgreed.get(term.getTermId()))) {
                throw new IllegalArgumentException(
                        "필수 약관 '" + term.getTitle() + "'에 동의해야 합니다."
                );
            }
        }

        // 프론트가 보낸 key가 실제 약관인지 검증 후 동의 기록 저장
        for (String termId : termsAgreed.keySet()) {
            Terms term = termsRepository.findByTermId(termId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "존재하지 않는 약관입니다: " + termId
                    ));

            boolean agreed = termsAgreed.getOrDefault(termId, false);

            // 필수 약관은 반드시 동의해야 함
            if (term.isRequired() && !agreed) {
                throw new IllegalArgumentException(
                        "필수 약관 '" + term.getTitle() + "'에 동의해야 합니다."
                );
            }

            UserTermsAgreement agreement = UserTermsAgreement.of(
                    userId, termId, term.getVersion(), agreed, ipAddress, userAgent
            );

            userTermsAgreementRepository.save(agreement);
        }

        log.info("약관 동의 저장 완료: userId={}, terms count={}", userId, termsAgreed.size());
    }

    // ── 사용자의 약관 동의 여부 조회
    public boolean hasAgreedToTerm(Long userId, String termId) {
        return userTermsAgreementRepository.hasAgreedToTerm(userId, termId);
    }

    // ── 사용자의 모든 약관 동의 이력 조회
    public List<UserTermsAgreement> getUserTermsAgreement(Long userId) {
        return userTermsAgreementRepository.findByUserId(userId);
    }

    // ── 사용자의 약관 동의 이력 삭제 (회원탈퇴 시)
    @Transactional
    public void deleteUserTermsAgreement(Long userId) {
        userTermsAgreementRepository.deleteByUserId(userId);
        log.info("약관 동의 이력 삭제: userId={}", userId);
    }

    // ── 약관 생성 또는 업데이트 (관리자용)
    @Transactional
    public Terms saveOrUpdateTerm(String termId, String title, String content,
                                  boolean isRequired, String version) {
        Terms term = termsRepository.findByTermId(termId)
                .orElseGet(() -> Terms.of(termId, title, content, isRequired, version));

        // 이미 존재하는 경우 버전 업데이트
        if (term.getId() != null) {
            term.setTitle(title);
            term.setContent(content);
            term.setRequired(isRequired);
            term.setVersion(version);
            term.setActive(true);
        }

        return termsRepository.save(term);
    }

    // ── 약관 활성화/비활성화 (관리자용)
    @Transactional
    public void setTermActive(String termId, boolean active) {
        Terms term = termsRepository.findByTermId(termId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "약관을 찾을 수 없습니다: " + termId
                ));

        term.setActive(active);
        termsRepository.save(term);
        log.info("약관 활성화 상태 변경: termId={}, active={}", termId, active);
    }
}
