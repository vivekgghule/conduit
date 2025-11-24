package com.conduit.egress.agent;

import com.conduit.egress.core.RateLimitBackend;
import com.conduit.egress.core.RateLimitConfig;
import com.conduit.egress.core.RateLimitKey;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;

public class RateLimitBackendHealthIndicator implements HealthIndicator {

    private final RateLimitBackend backend;
    private final Clock clock;

    public RateLimitBackendHealthIndicator(RateLimitBackend backend, Clock clock) {
        this.backend = backend;
        this.clock = clock;
    }

    @Override
    public Health health() {
        try {
            RateLimitConfig cfg = new RateLimitConfig(
                    "health-check",
                    1,
                    1,
                    Duration.ofSeconds(1),
                    EnumSet.noneOf(com.conduit.egress.core.RateLimitDimension.class)
            );
            RateLimitKey key = RateLimitKey.builder("health-check").build();
            boolean ok = backend.tryAcquire(key, 0, cfg, clock);
            if (ok) {
                return Health.up().build();
            }
            return Health.up().withDetail("note", "backend responded").build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
