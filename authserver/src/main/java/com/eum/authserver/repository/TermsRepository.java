package com.eum.authserver.repository;

import com.eum.authserver.entity.Terms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TermsRepository extends JpaRepository<Terms, Long> {

    // termId로 약관 조회 (service_terms, privacy_policy 등)
    Optional<Terms> findByTermId(String termId);

    // 활성화된 모든 약관 조회 (회원가입 시)
    List<Terms> findByActiveTrue();

    // 활성화된 필수 약관만 조회
    List<Terms> findByActiveTrueAndIsRequiredTrue();

    // 활성화된 선택 약관만 조회
    List<Terms> findByActiveTrueAndIsRequiredFalse();

    // termId 존재 여부 확인
    boolean existsByTermId(String termId);
}
