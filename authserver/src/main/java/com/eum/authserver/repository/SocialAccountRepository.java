package com.eum.authserver.repository;

import com.eum.authserver.entity.SocialAccount;
import com.eum.authserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    // provider + providerId로 소셜 로그인 계정 조회
    Optional<SocialAccount> findByProviderAndProviderId(String provider, String providerId);

    // 특정 유저의 특정 소셜 계정 존재 여부
    boolean existsByUserAndProvider(User user, String provider);
}
