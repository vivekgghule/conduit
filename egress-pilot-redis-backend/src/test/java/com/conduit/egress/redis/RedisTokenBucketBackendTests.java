package com.conduit.egress.redis;

import com.conduit.egress.core.RateLimitConfig;
import com.conduit.egress.core.RateLimitDimension;
import com.conduit.egress.core.RateLimitKey;
import com.conduit.egress.core.RateLimitSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RedisTokenBucketBackendTests {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOps;
    private RedisTokenBucketBackend backend;
    private Clock clock;

    static class FixedClock extends Clock {
        private final Instant instant;

        FixedClock(Instant instant) {
            this.instant = instant;
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

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        backend = new RedisTokenBucketBackend(redisTemplate, "test:");
        clock = new FixedClock(Instant.ofEpochMilli(1_000_000));
    }

    @Test
    void allowsWhenScriptReturnsAllowed() {
        doReturn(List.of(1L, 59L, 60L, 1_000_000L))
                .when(redisTemplate)
                .execute(
                        ArgumentMatchers.<org.springframework.data.redis.core.script.RedisScript<List>>any(),
                        anyList(),
                        any(), any(), any(), any(), any());

        RateLimitConfig cfg = new RateLimitConfig(
                "rule",
                60,
                60,
                Duration.ofSeconds(60),
                EnumSet.of(RateLimitDimension.HOST)
        );
        RateLimitKey key = RateLimitKey.builder("rule").host("api.github.com").build();

        boolean allowed = backend.tryAcquire(key, 1, cfg, clock);

        assertThat(allowed).isTrue();
        verify(redisTemplate).execute(any(), anyList(), any(), any(), any(), any(), any());
    }

    @Test
    void deniesWhenScriptReturnsDenied() {
        doReturn(List.of(0L, 0L, 60L, 1_000_000L))
                .when(redisTemplate)
                .execute(
                        ArgumentMatchers.<org.springframework.data.redis.core.script.RedisScript<List>>any(),
                        anyList(),
                        any(), any(), any(), any(), any());

        RateLimitConfig cfg = new RateLimitConfig(
                "rule",
                60,
                60,
                Duration.ofSeconds(60),
                EnumSet.of(RateLimitDimension.HOST)
        );
        RateLimitKey key = RateLimitKey.builder("rule").host("api.github.com").build();

        boolean allowed = backend.tryAcquire(key, 1, cfg, clock);

        assertThat(allowed).isFalse();
    }

    @Test
    void snapshotFallsBackToCapacityWhenNoState() {
        when(hashOps.multiGet(anyString(), anyList()))
                .thenReturn(Arrays.asList(null, null));

        RateLimitConfig cfg = new RateLimitConfig(
                "rule",
                60,
                60,
                Duration.ofSeconds(60),
                EnumSet.of(RateLimitDimension.HOST)
        );
        RateLimitKey key = RateLimitKey.builder("rule").host("api.github.com").build();

        RateLimitSnapshot snapshot = backend.getSnapshot(key, cfg, clock);

        assertThat(snapshot.getRemainingTokens()).isEqualTo(60);
        assertThat(snapshot.getCapacity()).isEqualTo(60);
    }

    @Test
    void snapshotParsesExistingState() {
        when(hashOps.multiGet(anyString(), anyList()))
                .thenReturn(Arrays.asList("42", "900000"));

        RateLimitConfig cfg = new RateLimitConfig(
                "rule",
                60,
                60,
                Duration.ofSeconds(60),
                EnumSet.of(RateLimitDimension.HOST)
        );
        RateLimitKey key = RateLimitKey.builder("rule").host("api.github.com").build();

        RateLimitSnapshot snapshot = backend.getSnapshot(key, cfg, clock);

        assertThat(snapshot.getRemainingTokens()).isEqualTo(42);
        assertThat(snapshot.getLastRefillEpochMillis()).isEqualTo(900000L);
    }
}
