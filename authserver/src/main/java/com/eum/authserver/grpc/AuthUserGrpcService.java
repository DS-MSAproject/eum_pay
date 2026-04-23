package com.eum.authserver.grpc;

import com.eum.authserver.entity.User;
import com.eum.authserver.repository.UserRepository;
import com.eum.grpc.auth.AuthUserServiceGrpc;
import com.eum.grpc.auth.UserByIdRequest;
import com.eum.grpc.auth.UserInfoResponse;
import io.grpc.Status;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class AuthUserGrpcService extends AuthUserServiceGrpc.AuthUserServiceImplBase {

    private final UserRepository userRepository;

    public AuthUserGrpcService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void getUserById(UserByIdRequest request,
                            io.grpc.stub.StreamObserver<UserInfoResponse> responseObserver) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("User not found: " + request.getUserId())
                        .asRuntimeException());

        UserInfoResponse response = UserInfoResponse.newBuilder()
                .setUserId(user.getId())
                .setUsername(safe(user.getUsername()))
                .setEmail(safe(user.getEmail()))
                .setName(safe(user.getName()))
                .setRole(user.getRole() != null ? user.getRole().name() : "")
                .setProvider(safe(user.getProvider()))
                .setEmailVerified(user.isEmailVerified())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
