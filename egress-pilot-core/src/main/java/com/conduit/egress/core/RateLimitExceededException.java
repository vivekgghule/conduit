package com.conduit.egress.core;

/**
 * Thrown when a rate limit bucket denies a request.
 */
public class RateLimitExceededException extends RuntimeException {

    private final RateLimitKey key;
    private final long retryAfterMillis;

    public RateLimitExceededException(RateLimitKey key, String message, long retryAfterMillis) {
        super(message);
        this.key = key;
        this.retryAfterMillis = retryAfterMillis;
    }

    public RateLimitKey getKey() {
        return key;
    }

    /**
     * Suggested time in milliseconds after which the caller may retry.
     */
    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}
