package com.eum.reviewserver.service;

import com.eum.grpc.auth.UserInfoResponse;
import com.eum.reviewserver.client.AuthGrpcClient;
import com.eum.reviewserver.dto.response.AuthUserGrpcResponse;
import com.eum.reviewserver.exception.ResourceNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

@Service
public class AuthGrpcLookupService {

    private final AuthGrpcClient authGrpcClient;

    public AuthGrpcLookupService(AuthGrpcClient authGrpcClient) {
        this.authGrpcClient = authGrpcClient;
    }

    public AuthUserGrpcResponse getUserById(Long userId) {
        try {
            UserInfoResponse response = authGrpcClient.getUserById(userId);
            return AuthUserGrpcResponse.builder()
                    .userId(response.getUserId())
                    .username(response.getUsername())
                    .email(response.getEmail())
                    .name(response.getName())
                    .role(response.getRole())
                    .provider(response.getProvider())
                    .emailVerified(response.getEmailVerified())
                    .build();
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("Auth user not found: " + userId);
            }
            throw new IllegalArgumentException("Failed to fetch auth user via gRPC: " + ex.getStatus().getDescription());
        }
    }
}
