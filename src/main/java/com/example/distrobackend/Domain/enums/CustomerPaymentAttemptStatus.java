package com.example.distrobackend.Domain.enums;

public enum CustomerPaymentAttemptStatus {
    INITIALIZING,
    PENDING,
    OUTCOME_UNKNOWN,
    SUCCEEDED,
    FAILED,
    EXPIRED
}
