package com.example.distrobackend.Util;



public final class PasswordRules {
    private PasswordRules() {}

    // 8-72 chars (BCrypt ignores anything beyond 72 bytes), at least one lower, upper and digit
    public static final String PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$";
    public static final String MESSAGE =
            "Password must be 8-72 characters and include an upper-case letter, a lower-case letter and a digit";
}