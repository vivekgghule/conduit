package com.conduit.egress.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitConfigTests {

    @Test
    void constructsWithValidValues() {
        RateLimitConfig cfg = new RateLimitConfig(
                "rule",
                100,
                10,
                Duration.ofSeconds(1),
                EnumSet.of(RateLimitDimension.HOST, RateLimitDimension.PATH)
        );

        assertThat(cfg.getName()).isEqualTo("rule");
        assertThat(cfg.getCapacity()).isEqualTo(100);
        assertThat(cfg.getRefillTokens()).isEqualTo(10);
        assertThat(cfg.getRefillPeriod()).isEqualTo(Duration.ofSeconds(1));
        assertThat(cfg.getDimensions()).containsExactlyInAnyOrder(
                RateLimitDimension.HOST, RateLimitDimension.PATH
        );
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThatThrownBy(() -> new RateLimitConfig(
                "x", 0, 1, Duration.ofSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidRefillTokens() {
        assertThatThrownBy(() -> new RateLimitConfig(
                "x", 10, 0, Duration.ofSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidRefillPeriod() {
        assertThatThrownBy(() -> new RateLimitConfig(
                "x", 10, 5, Duration.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRefillTokensGreaterThanCapacity() {
        assertThatThrownBy(() -> new RateLimitConfig(
                "x", 5, 10, Duration.ofSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
