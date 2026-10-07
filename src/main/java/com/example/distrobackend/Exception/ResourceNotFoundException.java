package com.example.distrobackend.Exception;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(ErrorCode code) {
        super(code);
    }

    public ResourceNotFoundException(ErrorCode code, String message) {
        super(code, message);
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
