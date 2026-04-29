package com.eum.authserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.eum.authserver.dto.*;
import com.eum.authserver.entity.Address;
import com.eum.authserver.entity.Profile;
import com.eum.authserver.entity.User;
import com.eum.authserver.repository.AddressRepository;
import com.eum.authserver.repository.ProfileRepository;
import com.eum.authserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final UserRepository      userRepository;
    private final ProfileRepository   profileRepository;
    private final AddressRepository   addressRepository;
    private final PasswordEncoder     passwordEncoder;
    private final WebClient.Builder   webClientBuilder;

    @Value("${app.services.order.base-url:http://dseum-order}")
    private String orderServiceBaseUrl;

    // ── 마이페이지 메인 조회 ─────────────────────────────────────────────
    public ProfileResponse getProfile(String email) {
        User user = getUser(email);
        Profile profile = getOrCreateProfile(user.getId());

        // 추후 다른 서버 연동 시 WebClient로 교체
        // couponserver → couponCount
        // orderserver  → orderTotalCount, orderStatusSummary
        // productserver → wishlistCount
        // boardserver  → postCount
        int couponCount     = 0;
        int orderTotalCount = fetchOrderTotalCount(user.getId());
        int wishlistCount   = 0;
        int postCount       = 0;

        return ProfileResponse.builder()
                .status("success")
                .data(ProfileResponse.Data.builder()
                        .userSummary(ProfileResponse.UserSummary.builder()
                                .id(user.getId())
                                .name(user.getName() != null ? user.getName() : "")
                                .profileImgUrl(user.getProfileImgUrl())
                                .greetingMessage(user.getName() + "님 안녕하세요!")
                                .membershipLevel("일반회원")
                                .build())
                        .benefits(ProfileResponse.Benefits.builder()
                                .points(profile.getPoint())
                                .couponCount(couponCount)
                                .orderTotalCount(orderTotalCount)
                                .build())
                        .orderStatusSummary(ProfileResponse.OrderStatusSummary.builder()
                                .recentPeriod("최근 3개월")
                                .mainStatuses(ProfileResponse.MainStatuses.builder()
                                        .pendingPayment(0)
                                        .preparing(0)
                                        .shipping(0)
                                        .delivered(0)
                                        .build())
                                .subStatuses(ProfileResponse.SubStatuses.builder()
                                        .cancelled(0)
                                        .exchanged(0)
                                        .returned(0)
                                        .build())
                                .build())
                        .activityCounts(ProfileResponse.ActivityCounts.builder()
                                .wishlistCount(wishlistCount)
                                .postCount(postCount)
                                .regularDeliveryCount(0)
                                .build())
                        .build())
                .build();
    }

    // ── 회원정보 수정 폼 조회 ────────────────────────────────────────────
    public UserProfileResponse getUserProfile(String email) {
        User user = getUser(email);
        Profile profile = getOrCreateProfile(user.getId());

        return UserProfileResponse.builder()
                .status("success")
                .data(UserProfileResponse.Data.builder()
                        .userId(user.getUsername())
                        .name(user.getName() != null ? user.getName() : "")
                        .email(user.getEmail())
                        .profileImgUrl(user.getProfileImgUrl())
                        .phoneNumber(user.getPhoneNumber() != null ? user.getPhoneNumber() : "")
                        .smsAllowed(profile.isSmsAllowed())
                        .emailAllowed(profile.isEmailAllowed())
                        .updatedAt(user.getUpdatedAt())
                        .build())
                .build();
    }

    // ── 회원정보 수정 ────────────────────────────────────────────────────
    @Transactional
    public UserProfileResponse updateUserProfile(String email, UserUpdateRequest request) {
        User user = getUser(email);
        Profile profile = getOrCreateProfile(user.getId());

        // 아이디(username), 이름(name), 이메일(email)은 회원정보 수정 화면에서 읽기 전용이다.
        // 프론트가 값을 보내더라도 서버에서는 변경하지 않는다.

        // 전화번호 수정
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            // 다른 유저가 사용 중인 전화번호인지 확인
            userRepository.findByPhoneNumber(request.getPhoneNumber())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(user.getId())) {
                            throw new IllegalArgumentException("이미 사용 중인 전화번호입니다.");
                        }
                    });
            user.setPhoneNumber(request.getPhoneNumber());
        }

        // 비밀번호 변경 (선택)
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null) {
                throw new IllegalArgumentException("현재 비밀번호를 입력해주세요.");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
            }
            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                throw new IllegalArgumentException("새 비밀번호가 일치하지 않습니다.");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        // 마케팅 수신 동의 수정
        if (request.getMarketingConsent() != null) {
            if (request.getMarketingConsent().getSmsAllowed() != null) {
                profile.setSmsAllowed(request.getMarketingConsent().getSmsAllowed());
            }
            if (request.getMarketingConsent().getEmailAllowed() != null) {
                profile.setEmailAllowed(request.getMarketingConsent().getEmailAllowed());
            }
        }

        userRepository.save(user);
        profileRepository.save(profile);

        return getUserProfile(email);
    }

    // ── 회원 탈퇴 ────────────────────────────────────────────────────────
    @Transactional
    public void deleteUser(String email, String password) {
        User user = getUser(email);

        // 소셜 전용 계정은 비밀번호 없음 → 비밀번호 확인 스킵
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
                throw new IllegalArgumentException("비밀번호가 올바르지 않습니다.");
            }
        }

        // 프로필, 주소 삭제
        profileRepository.findByUserId(user.getId())
                .ifPresent(profileRepository::delete);
        addressRepository.deleteByUserId(user.getId());

        userRepository.delete(user);
        log.info("회원 탈퇴 완료: userId={}, email={}", user.getId(), email);
    }

    // ── 주소 목록 조회 ───────────────────────────────────────────────────
    public AddressResponse getAddresses(String email) {
        User user = getUser(email);
        List<Address> addresses = addressRepository
                .findByUserIdOrderByIsDefaultDescCreatedAtDesc(user.getId());

        List<AddressResponse.AddressItem> items = addresses.stream()
                .map(AddressResponse.AddressItem::from)
                .collect(Collectors.toList());

        return AddressResponse.builder()
                .status("success")
                .data(AddressResponse.Data.builder()
                        .totalCount(items.size())
                        .addresses(items)
                        .build())
                .build();
    }

    // ── 주소 등록 ────────────────────────────────────────────────────────
    @Transactional
    public AddressResponse.AddressItem createAddress(String email, AddressRequest request) {
        User user = getUser(email);

        Address address = new Address();
        address.setUserId(user.getId());
        address.setAddressName(
                request.getAddressName() == null || request.getAddressName().isBlank()
                        ? "미지정" : request.getAddressName()
        );
        address.setRecipientName(resolveRecipientName(user));
        address.setZipCode(request.getPostcode());
        address.setBaseAddress(trimToNull(request.getBaseAddress()));
        address.setDetailAddress(trimToNull(request.getDetailAddress()));
        address.setExtraAddress(trimToNull(request.getExtraAddress()));
        address.setAddressType(trimToNull(request.getAddressType()));
        address.setPhoneNumber(resolvePhoneNumber(user));
        address.setDefault(request.isDefault());

        // 기본 배송지로 설정 시 기존 기본 배송지 해제
        if (request.isDefault()) {
            addressRepository.clearDefaultByUserId(user.getId());
        }

        // 첫 번째 주소는 자동으로 기본 배송지
        if (addressRepository.countByUserId(user.getId()) == 0) {
            address.setDefault(true);
        }

        Address saved = addressRepository.save(address);
        return AddressResponse.AddressItem.from(saved);
    }

    // ── 주소 수정 ────────────────────────────────────────────────────────
    @Transactional
    public AddressResponse.AddressItem updateAddress(String email, Long addressId,
                                                     AddressRequest request) {
        User user = getUser(email);
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("배송지를 찾을 수 없습니다."));

        if (request.getAddressName() != null) {
            address.setAddressName(request.getAddressName().isBlank()
                    ? "미지정" : request.getAddressName());
        }
        if (request.getPostcode() != null)      address.setZipCode(request.getPostcode().trim());
        if (request.getBaseAddress() != null)   address.setBaseAddress(trimToNull(request.getBaseAddress()));
        if (request.getDetailAddress() != null) address.setDetailAddress(trimToNull(request.getDetailAddress()));
        if (request.getExtraAddress() != null)  address.setExtraAddress(trimToNull(request.getExtraAddress()));
        if (request.getAddressType() != null)   address.setAddressType(trimToNull(request.getAddressType()));

        if (request.isDefault()) {
            addressRepository.clearDefaultByUserId(user.getId());
            address.setDefault(true);
        }

        Address saved = addressRepository.save(address);
        return AddressResponse.AddressItem.from(saved);
    }

    // ── 주소 삭제 ────────────────────────────────────────────────────────
    @Transactional
    public void deleteAddress(String email, Long addressId) {
        User user = getUser(email);
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("배송지를 찾을 수 없습니다."));

        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        // 삭제된 주소가 기본 배송지였으면 가장 최근 주소를 기본으로 설정
        if (wasDefault) {
            addressRepository
                    .findByUserIdOrderByIsDefaultDescCreatedAtDesc(user.getId())
                    .stream().findFirst()
                    .ifPresent(next -> {
                        next.setDefault(true);
                        addressRepository.save(next);
                    });
        }
    }

    // ── 프로필 생성 (회원가입/소셜 로그인 시 호출) ──────────────────────
    @Transactional
    public Profile createProfile(Long userId) {
        if (profileRepository.existsByUserId(userId)) {
            return profileRepository.findByUserId(userId).get();
        }
        return profileRepository.save(Profile.createDefault(userId));
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────
    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    private Profile getOrCreateProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseGet(() -> profileRepository.save(Profile.createDefault(userId)));
    }

    private int fetchOrderTotalCount(Long userId) {
        try {
            JsonNode response = webClientBuilder.build()
                    .get()
                    .uri(orderServiceBaseUrl + "/orders?page=1&size=1")
                    .header("X-User-Id", String.valueOf(userId))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(2));

            if (response == null || !response.has("totalElements")) {
                log.warn("주문 수 조회 응답에 totalElements가 없습니다. userId={}", userId);
                return 0;
            }
            return response.get("totalElements").asInt(0);
        } catch (Exception e) {
            log.warn("주문 수 조회 실패. userId={}, message={}", userId, e.getMessage());
            return 0;
        }
    }

    private String resolveRecipientName(User user) {
        if (user.getName() == null || user.getName().isBlank()) {
            throw new IllegalArgumentException("사용자 이름 정보가 없어 배송지를 저장할 수 없습니다.");
        }
        return user.getName().trim();
    }

    private String resolvePhoneNumber(User user) {
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            throw new IllegalArgumentException("사용자 전화번호 정보가 없어 배송지를 저장할 수 없습니다.");
        }
        return user.getPhoneNumber().trim();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
