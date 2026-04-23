package com.eum.authserver.repository;

import com.eum.authserver.entity.UserTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {

    // 사용자의 모든 약관 동의 이력 조회
    List<UserTermsAgreement> findByUserId(Long userId);

    // 특정 약관의 사용자 동의 여부 조회
    Optional<UserTermsAgreement> findByUserIdAndTermId(Long userId, String termId);

    // 사용자의 특정 약관 동의 여부 확인
    boolean existsByUserIdAndTermId(Long userId, String termId);

    // 사용자의 모든 약관 삭제 (회원탈퇴 시)
    @Modifying
    @Query("DELETE FROM UserTermsAgreement u WHERE u.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    // 사용자가 특정 약관에 동의했는지 확인
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
            "FROM UserTermsAgreement u WHERE u.userId = :userId AND u.termId = :termId AND u.agreed = true")
    boolean hasAgreedToTerm(@Param("userId") Long userId, @Param("termId") String termId);
}