package com.example.distrobackend.Exception;



import org.springframework.http.HttpStatus;

/** Stable machine-readable codes the Angular/mobile clients can switch on. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email/phone or password"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid token"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token has expired"),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to access this resource"),
    ROLE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "This role is not allowed for this action"),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Account is not verified. Enter the code sent to your phone"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "Account is suspended. Contact your administrator"),
    ACCOUNT_DEACTIVATED(HttpStatus.FORBIDDEN, "Account is deactivated"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this endpoint"),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "An account with this email already exists"),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "An account with this phone number already exists"),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with existing data"),

    INVALID_OTP(HttpStatus.BAD_REQUEST, "The verification code is incorrect"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "The verification code has expired. Request a new one"),
    OTP_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect attempts. Request a new code"),
    OTP_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "Please wait a moment before requesting another code"),

    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "This feature is not yet implemented"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() { return status; }
    public String defaultMessage() { return defaultMessage; }
}