package com.example.distrobackend;

import com.example.distrobackend.Util.SecretTokenService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretTokenServiceTest {
    @Test
    void generatesUrlSafeTokensAndOnlyHashesAreComparable() {
        String raw = SecretTokenService.generate();
        String hash = SecretTokenService.sha256(raw);

        assertThat(raw).isNotBlank().doesNotContain("+").doesNotContain("/");
        assertThat(hash).hasSize(64);
        assertThat(SecretTokenService.matches(raw, hash)).isTrue();
        assertThat(SecretTokenService.matches(raw + "x", hash)).isFalse();
    }
}
