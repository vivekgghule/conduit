package com.conduit.egress.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTokenBucketBackendTests {

    static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void plusMillis(long millis) {
            this.instant = this.instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Test
    void acquiresWithinCapacity() {
        InMemoryTokenBucketBackend backend = new InMemoryTokenBucketBackend();
        MutableClock clock = new MutableClock(Instant.now());
        RateLimitConfig config = new RateLimitConfig("test", 10, 5, Duration.ofSeconds(1), null);
        RateLimitKey key = RateLimitKey.builder("test").build();

        assertThat(backend.tryAcquire(key, 1, config, clock)).isTrue();
    }

    @Test
    void deniesWhenExhausted() {
        InMemoryTokenBucketBackend backend = new InMemoryTokenBucketBackend();
        MutableClock clock = new MutableClock(Instant.now());
        RateLimitConfig config = new RateLimitConfig("test", 3, 1, Duration.ofSeconds(10), null);
        RateLimitKey key = RateLimitKey.builder("test").build();

        assertThat(backend.tryAcquire(key, 1, config, clock)).isTrue();
        assertThat(backend.tryAcquire(key, 1, config, clock)).isTrue();
        assertThat(backend.tryAcquire(key, 1, config, clock)).isTrue();
        assertThat(backend.tryAcquire(key, 1, config, clock)).isFalse();
    }

    @Test
    void refillsOverTime() {
        InMemoryTokenBucketBackend backend = new InMemoryTokenBucketBackend();
        MutableClock clock = new MutableClock(Instant.now());
        RateLimitConfig config = new RateLimitConfig("test", 2, 1, Duration.ofSeconds(1), null);
        RateLimitKey key = RateLimitKey.builder("test").build();

        assertThat(backend.tryAcquire(key, 2, config, clock)).isTrue();
        assertThat(backend.tryAcquire(key, 1, config, clock)).isFalse();

        clock.plusMillis(1100);
        assertThat(backend.tryAcquire(key, 1, config, clock)).isTrue();
    }

    @Test
    void invalidConfigThrows() {
        assertThatThrownBy(() -> new RateLimitConfig("bad", 0, 1, Duration.ofSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitConfig("bad", 10, 0, Duration.ofSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitConfig("bad", 10, 5, Duration.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
