package com.eum.authserver.exception;

import lombok.Getter;

@Getter
public class AccountLockedException extends RuntimeException {

    private final long remainSeconds;

    public AccountLockedException(long remainSeconds) {
        super(String.format(
                "로그인 시도 횟수를 초과했습니다. %d초 후 다시 시도해주세요.", remainSeconds
        ));
        this.remainSeconds = remainSeconds;
    }
}