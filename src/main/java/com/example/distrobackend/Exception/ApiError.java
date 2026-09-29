package com.example.distrobackend.Exception;



import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** The single error shape returned by every endpoint and by the security filters. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {
    public record FieldViolation(String field, String message) {}

    public static ApiError of(ErrorCode code, String message, String path) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, null);
    }

    public static ApiError of(ErrorCode code, String message, String path, List<FieldViolation> fieldErrors) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, fieldErrors);
    }
}