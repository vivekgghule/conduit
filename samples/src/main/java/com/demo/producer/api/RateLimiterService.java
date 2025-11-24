package com.demo.producer.api;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    private final int limit = 3;
    private final Duration window = Duration.ofSeconds(1);

    private final AtomicInteger counter = new AtomicInteger(0);
    private volatile Instant windowStart = Instant.now();

    public synchronized boolean allow() {
        Instant now = Instant.now();
        if (now.isAfter(windowStart.plus(window))) {
            windowStart = now;
            counter.set(0);
        }
        if (counter.get() >= limit) {
            return false;
        }
        counter.incrementAndGet();
        return true;
    }
}
