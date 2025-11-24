package com.conduit.egress.core;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;

/**
 * Immutable description of a single rate limit.
 */
public final class RateLimitConfig {

    private final String name;
    private final long capacity;
    private final long refillTokens;
    private final Duration refillPeriod;
    private final EnumSet<RateLimitDimension> dimensions;

    public RateLimitConfig(
            String name,
            long capacity,
            long refillTokens,
            Duration refillPeriod,
            EnumSet<RateLimitDimension> dimensions
    ) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("refillTokens must be > 0");
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("refillPeriod must be positive");
        }
        if (capacity < refillTokens) {
            throw new IllegalArgumentException("capacity must be >= refillTokens");
        }
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriod = refillPeriod;
        this.dimensions = dimensions == null
                ? EnumSet.of(RateLimitDimension.HOST, RateLimitDimension.PATH)
                : dimensions.clone();
    }

    public String getName() {
        return name;
    }

    public long getCapacity() {
        return capacity;
    }

    public long getRefillTokens() {
        return refillTokens;
    }

    public Duration getRefillPeriod() {
        return refillPeriod;
    }

    public EnumSet<RateLimitDimension> getDimensions() {
        return dimensions.clone();
    }
}
