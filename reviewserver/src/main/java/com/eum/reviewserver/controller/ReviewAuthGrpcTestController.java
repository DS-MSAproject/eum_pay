package com.eum.reviewserver.controller;

import com.eum.reviewserver.dto.response.AuthUserGrpcResponse;
import com.eum.reviewserver.service.AuthGrpcLookupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews/grpc-test")
public class ReviewAuthGrpcTestController {

    private final AuthGrpcLookupService authGrpcLookupService;

    public ReviewAuthGrpcTestController(AuthGrpcLookupService authGrpcLookupService) {
        this.authGrpcLookupService = authGrpcLookupService;
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AuthUserGrpcResponse> getAuthUser(@PathVariable Long userId) {
        return ResponseEntity.ok(authGrpcLookupService.getUserById(userId));
    }
}
