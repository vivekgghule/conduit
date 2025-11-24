package com.conduit.egress.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTokenBucketBackendConcurrencyTests {

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

    @Test
    void concurrentAcquireDoesNotProduceNegativeTokens() throws InterruptedException {
        InMemoryTokenBucketBackend backend = new InMemoryTokenBucketBackend();
        Clock clock = new FixedClock(Instant.now());
        RateLimitConfig cfg = new RateLimitConfig("rule", 100, 10, Duration.ofSeconds(60), null);
        RateLimitKey key = RateLimitKey.builder("rule").build();

        int threads = 50;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();

        Runnable task = () -> {
            if (backend.tryAcquire(key, 1, cfg, clock)) {
                successCount.incrementAndGet();
            }
            latch.countDown();
        };

        for (int i = 0; i < threads; i++) {
            new Thread(task).start();
        }

        latch.await();

        RateLimitSnapshot snapshot = backend.getSnapshot(key, cfg, clock);
        assertThat(snapshot.getRemainingTokens()).isGreaterThanOrEqualTo(0);
        assertThat(successCount.get()).isLessThanOrEqualTo(100);
    }
}
