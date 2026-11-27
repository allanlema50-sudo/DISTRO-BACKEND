package com.example.distrobackend.Domain.enums;

public enum CustomerPaymentAttemptStatus {
    INITIALIZING,
    PENDING,
    OUTCOME_UNKNOWN,
    REVIEW_REQUIRED,
    SUCCEEDED,
    FAILED,
    EXPIRED
}
