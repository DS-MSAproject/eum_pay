package com.eum.cartserver.exception;

public class InvalidCartRequestException extends RuntimeException {
    public InvalidCartRequestException(String message) {
        super(message);
    }
}
