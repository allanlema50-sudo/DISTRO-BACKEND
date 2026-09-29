package com.example.distrobackend.security;

import com.example.distrobackend.Exception.ApiError;
import com.example.distrobackend.Exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * 401 as JSON.
 * Filter-level failures never reach @RestControllerAdvice,
 * so they are handled here.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        Object jwtError = request.getAttribute(JwtAuthFilter.AUTH_ERROR_ATTRIBUTE);

        ErrorCode code =
                "TOKEN_EXPIRED".equals(jwtError)
                        ? ErrorCode.TOKEN_EXPIRED
                        : "INVALID_TOKEN".equals(jwtError)
                        ? ErrorCode.INVALID_TOKEN
                        : ErrorCode.UNAUTHENTICATED;

        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        jsonMapper.writeValue(
                response.getOutputStream(),
                ApiError.of(
                        code,
                        code.defaultMessage(),
                        request.getRequestURI()
                )
        );
    }
}