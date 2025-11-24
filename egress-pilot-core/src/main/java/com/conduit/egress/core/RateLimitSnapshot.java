package com.conduit.egress.core;

/**
 * Immutable snapshot of a bucket's state.
 */
public final class RateLimitSnapshot {

    private final long remainingTokens;
    private final long capacity;
    private final long lastRefillEpochMillis;

    public RateLimitSnapshot(long remainingTokens, long capacity, long lastRefillEpochMillis) {
        this.remainingTokens = remainingTokens;
        this.capacity = capacity;
        this.lastRefillEpochMillis = lastRefillEpochMillis;
    }

    public long getRemainingTokens() {
        return remainingTokens;
    }

    public long getCapacity() {
        return capacity;
    }

    public long getLastRefillEpochMillis() {
        return lastRefillEpochMillis;
    }
}
