package com.example.distrobackend.service;




import com.example.distrobackend.Domain.entity.RefreshToken;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens are opaque random strings. Only their SHA-256 hash is stored, so a database leak
 * does not expose usable tokens. Every refresh rotates the token; reusing an old one revokes them all.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;

    @Value("${app.jwt.refresh-token-ttl-days:7}")
    private long refreshTtlDays;

    /** Creates a token and returns the RAW value (shown to the client once, never stored). */
    @Transactional
    public String create(User user) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(OffsetDateTime.now().plusDays(refreshTtlDays));
        repository.save(token);
        return raw;
    }

    /** Validates the token, marks it used (revoked) and returns its owner. Caller then issues a new pair. */
    @Transactional(noRollbackFor = ApiException.class)
    public User consume(String raw) {
        RefreshToken token = repository.findByTokenHash(sha256(raw))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));

        if (token.isRevoked()) {
            // A used token came back: likely stolen. Kill every session for this user.
            repository.revokeAllForUser(token.getUser().getId());
            throw new ApiException(ErrorCode.INVALID_TOKEN, "Session is no longer valid. Please log in again");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Session expired. Please log in again");
        }

        token.setRevoked(true);
        return token.getUser();
    }

    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(sha256(raw)).ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}