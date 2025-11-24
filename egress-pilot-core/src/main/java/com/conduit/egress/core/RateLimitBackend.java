package com.conduit.egress.core;

import java.time.Clock;

/**
 * Pluggable backend contract for token-bucket based rate limiting.
 */
public interface RateLimitBackend {

    /**
     * Try to acquire tokens for the given key.
     *
     * @param key          logical bucket key
     * @param permits      number of tokens requested (must be > 0)
     * @param config       static rate limit configuration
     * @param clock        time source
     * @return true if tokens were acquired, false if the request must be throttled
     */
    boolean tryAcquire(
            RateLimitKey key,
            long permits,
            RateLimitConfig config,
            Clock clock
    );

    /**
     * Snapshots the current state of the bucket.
     */
    RateLimitSnapshot getSnapshot(RateLimitKey key, RateLimitConfig config, Clock clock);
}
