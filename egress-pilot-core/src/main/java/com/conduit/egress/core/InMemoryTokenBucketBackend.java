package com.conduit.egress.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory token bucket implementation, safe for concurrent use in a single JVM.
 */
public class InMemoryTokenBucketBackend implements RateLimitBackend {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTokenBucketBackend.class);

    private static final class BucketState {
        private final AtomicLong tokens;
        private final AtomicLong lastRefillNanos;

        BucketState(long tokens, long lastRefillNanos) {
            this.tokens = new AtomicLong(tokens);
            this.lastRefillNanos = new AtomicLong(lastRefillNanos);
        }
    }

    private final Map<RateLimitKey, BucketState> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(RateLimitKey key, long permits, RateLimitConfig config, Clock clock) {
        if (permits <= 0) {
            return true;
        }

        long nowNanos = toEpochNanos(clock.instant());
        BucketState state = buckets.computeIfAbsent(
                key,
                k -> new BucketState(config.getCapacity(), nowNanos)
        );

        refill(state, config, nowNanos);

        while (true) {
            long current = state.tokens.get();
            if (current < permits) {
                return false;
            }
            long updated = current - permits;
            if (state.tokens.compareAndSet(current, updated)) {
                return true;
            }
        }
    }

    private void refill(BucketState state, RateLimitConfig config, long nowNanos) {
        long periodNanos = config.getRefillPeriod().toNanos();
        if (periodNanos <= 0) {
            return;
        }

        while (true) {
            long lastRefill = state.lastRefillNanos.get();
            long elapsed = nowNanos - lastRefill;
            if (elapsed < periodNanos) {
                return;
            }
            long periods = elapsed / periodNanos;
            if (periods <= 0) {
                return;
            }
            long currentTokens = state.tokens.get();
            long tokensToAdd = periods * config.getRefillTokens();
            long newTokens = Math.min(config.getCapacity(), currentTokens + tokensToAdd);
            long newRefillNanos = lastRefill + periods * periodNanos;

            if (state.tokens.compareAndSet(currentTokens, newTokens)
                    && state.lastRefillNanos.compareAndSet(lastRefill, newRefillNanos)) {
                return;
            }
            // CAS failed, retry
        }
    }

    @Override
    public RateLimitSnapshot getSnapshot(RateLimitKey key, RateLimitConfig config, Clock clock) {
        BucketState state = buckets.get(key);
        if (state == null) {
            return new RateLimitSnapshot(config.getCapacity(), config.getCapacity(), clock.millis());
        }
        return new RateLimitSnapshot(state.tokens.get(), config.getCapacity(), clock.millis());
    }

    private static long toEpochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
