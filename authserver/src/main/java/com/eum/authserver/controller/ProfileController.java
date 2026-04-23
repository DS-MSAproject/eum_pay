package com.eum.authserver.controller;

import com.eum.authserver.dto.*;
import com.eum.authserver.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    // ── 마이페이지 메인 ──────────────────────────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile(
            @RequestHeader(value = "X-User-Email", required = false) String email) {
        if (email == null)
            return ResponseEntity.status(401).build();
        return ResponseEntity.ok(profileService.getProfile(email));
    }

    // ── 회원정보 수정 폼 조회 ────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @RequestHeader(value = "X-User-Email", required = false) String email) {
        if (email == null)
            return ResponseEntity.status(401).build();
        return ResponseEntity.ok(profileService.getUserProfile(email));
    }

    // ── 회원정보 수정 ────────────────────────────────────────────────────
    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestBody @Valid UserUpdateRequest request) {
        if (email == null)
            return ResponseEntity.status(401).build();
        return ResponseEntity.ok(profileService.updateUserProfile(email, request));
    }

    // ── 회원 탈퇴 ────────────────────────────────────────────────────────
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteUser(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestBody(required = false) Map<String, String> body) {
        if (email == null)
            return ResponseEntity.status(401).build();

        String password = body != null ? body.get("password") : null;
        profileService.deleteUser(email, password);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "data", Map.of("withdrawal_date", LocalDateTime.now().toString())
        ));
    }

    // ── 주소 목록 조회 ───────────────────────────────────────────────────
    @GetMapping("/addresses")
    public ResponseEntity<AddressResponse> getAddresses(
            @RequestHeader(value = "X-User-Email", required = false) String email) {
        if (email == null)
            return ResponseEntity.status(401).build();
        return ResponseEntity.ok(profileService.getAddresses(email));
    }

    // ── 주소 등록 ────────────────────────────────────────────────────────
    @PostMapping("/addresses")
    public ResponseEntity<Map<String, Object>> createAddress(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestBody @Valid AddressRequest request) {
        if (email == null)
            return ResponseEntity.status(401).build();

        AddressResponse.AddressItem saved = profileService.createAddress(email, request);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "data", saved
        ));
    }

    // ── 주소 수정 ────────────────────────────────────────────────────────
    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<Map<String, Object>> updateAddress(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @PathVariable Long addressId,
            @RequestBody @Valid AddressRequest request) {
        if (email == null)
            return ResponseEntity.status(401).build();

        AddressResponse.AddressItem updated = profileService.updateAddress(email, addressId, request);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "data", updated
        ));
    }

    // ── 주소 삭제 ────────────────────────────────────────────────────────
    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Map<String, Object>> deleteAddress(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @PathVariable Long addressId) {
        if (email == null)
            return ResponseEntity.status(401).build();

        profileService.deleteAddress(email, addressId);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "data", Map.of("deleted_address_id", addressId)
        ));
    }
}
