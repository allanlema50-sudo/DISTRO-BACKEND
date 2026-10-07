package com.example.distrobackend.Exception;

public class InvalidStateTransitionException extends ApiException {
    public InvalidStateTransitionException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }
}
