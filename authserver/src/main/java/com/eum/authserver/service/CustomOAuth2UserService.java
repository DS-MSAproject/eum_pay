package com.eum.authserver.service;

import com.eum.authserver.entity.Role;
import com.eum.authserver.entity.SocialAccount;
import com.eum.authserver.entity.User;
import com.eum.authserver.oauth2.OAuth2UserInfo;
import com.eum.authserver.oauth2.OAuth2UserInfoFactory;
import com.eum.authserver.repository.SocialAccountRepository;
import com.eum.authserver.repository.UserRepository;
import com.eum.authserver.repository.ProfileRepository;
import com.eum.authserver.entity.Profile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository          userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final ProfileRepository       profileRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory
                .getOAuth2UserInfo(registrationId, oAuth2User.getAttributes());

        User user = processOAuth2User(userInfo);

        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(user.getRole().getKey())),
                oAuth2User.getAttributes(),
                userNameAttributeName
        );
    }

    private User processOAuth2User(OAuth2UserInfo userInfo) {
        String provider      = userInfo.getProvider();
        String providerId    = userInfo.getProviderId();
        String email         = userInfo.getEmail();
        String profileImgUrl = userInfo.getProfileImg();
        String phoneNumber   = userInfo.getPhoneNumber();

        // 1. 기존 소셜 로그인 계정 확인
        Optional<SocialAccount> existingSocial =
                socialAccountRepository.findByProviderAndProviderId(provider, providerId);

        if (existingSocial.isPresent()) {
            User user = existingSocial.get().getUser();
            user.setName(userInfo.getName());
            if (user.getPhoneNumber() == null && phoneNumber != null) {
                user.setPhoneNumber(phoneNumber);
            }
            // ✅ 소셜에서 받은 프로필 이미지 URL을 그대로 저장
            if (user.getProfileImgUrl() == null && profileImgUrl != null) {
                user.setProfileImgUrl(profileImgUrl);
            }
            return userRepository.save(user);
        }

        // 2. 동일 이메일로 가입된 계정 확인
        // 기존 로컬/다른 계정과 자동 병합하지 않고 명시적으로 로그인 실패 처리
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            log.warn("소셜 로그인 차단 - 기존 이메일 존재: email={}, provider={}", email, provider);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_already_exists"),
                    "현재 이메일은 존재하는 이메일입니다."
            );
        }

        // 3. 완전히 신규 유저 → 계정 생성
        return createNewSocialUser(userInfo);
    }

    private User createNewSocialUser(OAuth2UserInfo userInfo) {
        User user = new User();
        user.setEmail(userInfo.getEmail());
        user.setName(userInfo.getName());
        user.setProvider(userInfo.getProvider());
        user.setProviderId(userInfo.getProviderId());
        user.setRole(Role.USER);
        user.setEmailVerified(true);

        if (userInfo.getPhoneNumber() != null) {
            user.setPhoneNumber(userInfo.getPhoneNumber());
        }

        user.setUsername(userInfo.getProvider() + "_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8));

        User saved = userRepository.save(user);

        // ✅ 소셜에서 받은 프로필 이미지 URL을 그대로 저장
        if (userInfo.getProfileImg() != null) {
            saved.setProfileImgUrl(userInfo.getProfileImg());
            userRepository.save(saved);
        }

        createSocialAccount(saved, userInfo.getProvider(), userInfo.getProviderId());

        // Profile 동시 생성 (1:1)
        if (!profileRepository.existsByUserId(saved.getId())) {
            profileRepository.save(Profile.createDefault(saved.getId()));
        }

        log.info("신규 소셜 유저 생성: email={}, provider={}, username={}",
                saved.getEmail(), saved.getProvider(), saved.getUsername());
        return saved;
    }

    @Transactional
    public void createSocialAccount(User user, String provider, String providerId) {
        if (!socialAccountRepository.existsByUserAndProvider(user, provider)) {
            socialAccountRepository.save(SocialAccount.of(user, provider, providerId));
            log.info("소셜 계정 생성 완료: userId={}, provider={}", user.getId(), provider);
        }
    }
}
