package com.eum.authserver.controller;

import com.eum.authserver.dto.admin.AdminUserResponse;
import com.eum.authserver.entity.User;
import com.eum.authserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<User> users = userRepository.searchByKeyword(
                keyword.isBlank() ? null : keyword,
                PageRequest.of(page, size));

        return ResponseEntity.ok(users.map(AdminUserResponse::from));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable Long userId) {
        return userRepository.findById(userId)
                .map(u -> ResponseEntity.ok(AdminUserResponse.from(u)))
                .orElse(ResponseEntity.notFound().build());
    }
}
