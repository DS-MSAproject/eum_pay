package com.eum.reviewserver.client;

import com.eum.grpc.auth.AuthUserServiceGrpc;
import com.eum.grpc.auth.UserByIdRequest;
import com.eum.grpc.auth.UserInfoResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class AuthGrpcClient {

    @GrpcClient("auth-service")
    private AuthUserServiceGrpc.AuthUserServiceBlockingStub authUserServiceBlockingStub;

    public UserInfoResponse getUserById(Long userId) {
        return authUserServiceBlockingStub.getUserById(
                UserByIdRequest.newBuilder()
                        .setUserId(userId)
                        .build()
        );
    }
}
