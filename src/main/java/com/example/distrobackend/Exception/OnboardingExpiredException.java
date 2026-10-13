package com.example.distrobackend.Exception;

/** A persisted expiry transition must commit even though the request returns an error. */
public class OnboardingExpiredException extends ApiException {
    public OnboardingExpiredException(ErrorCode code) {
        super(code);
    }
}
