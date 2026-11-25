package com.example.distrobackend.Domain.enums;

public enum PaymentStatus {
    INITIATING,
    PENDING,
    CONFIRMED,
    FAILED,
    REVERSED,
    /**
     * Paid, and confirmed a second way through Daraja's STK query: either the
     * callback never arrived and the query reported success, or the nightly
     * payment-ledger consistency check found the query success and a captured
     * journal equal to the amount. Not matched against an M-Pesa statement.
     */
    RECONCILED
}
