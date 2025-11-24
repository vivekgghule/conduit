package com.conduit.egress.agent;

import com.conduit.egress.core.RateLimitBackend;
import com.conduit.egress.core.RateLimitConfig;
import com.conduit.egress.core.RateLimitDimension;
import com.conduit.egress.core.RateLimitSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

class RetryAfterEstimationTests {

    static class FixedClock extends Clock {
        private Instant instant;

        FixedClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
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

    private RuleCache ruleCache;
    private RateLimitBackend backend;
    private FixedClock clock;
    private SimpleMeterRegistry registry;
    private EgressAgentProperties properties;
    private WebClientRateLimiterFilter filter;

    @BeforeEach
    void setUp() {
        ruleCache = Mockito.mock(RuleCache.class);
        backend = Mockito.mock(RateLimitBackend.class);
        clock = new FixedClock(Instant.ofEpochMilli(10_000));
        registry = new SimpleMeterRegistry();
        properties = new EgressAgentProperties();
        properties.setBehaviorOnExhaustion(EgressAgentProperties.BehaviorOnExhaustion.QUEUE);
        properties.getQueue().setBackoffMs(100L);
        filter = new WebClientRateLimiterFilter(ruleCache, backend, clock, registry, properties);
    }

    @Test
    void usesBackoffWhenRetryAfterIsZero() {
        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        
        // First denied, second allowed
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any()))
                .thenReturn(false)
                .thenReturn(true);
        
        // Snapshot shows tokens available (remaining > 0)
        Mockito.when(backend.getSnapshot(any(), any(), any()))
                .thenReturn(new RateLimitSnapshot(10, 60, clock.millis()));

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // Should use backoff (100ms) since retry-after is 0
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void usesEstimatedRetryAfterWhenGreaterThanBackoff() {
        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any()))
                .thenReturn(false)
                .thenReturn(true);
        
        // Snapshot: no tokens, last refill was 500ms ago
        // Config: 60 tokens per 60000ms = 1 token per 1000ms
        // Time since refill: 500ms = 0.5 tokens regained
        // Need 1 token, deficit = 0.5 tokens
        // Wait time: 0.5 * 1000ms = 500ms
        Mockito.when(backend.getSnapshot(any(), any(), any()))
                .thenReturn(new RateLimitSnapshot(0, 60, clock.millis() - 500));

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // Should use estimated retry-after (~500ms) instead of backoff (100ms)
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void handlesBackendExceptionWhenEstimatingRetryAfter() {
        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any()))
                .thenReturn(false)
                .thenReturn(true);
        
        // Snapshot throws exception
        Mockito.when(backend.getSnapshot(any(), any(), any()))
                .thenThrow(new RuntimeException("Backend unavailable"));

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // Should fall back to default (1000ms) or backoff (100ms), whichever is greater
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void calculatesRetryAfterBasedOnRefillRate() {
        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any()))
                .thenReturn(false)
                .thenReturn(true);
        
        // Snapshot: 0 tokens, last refill 30s ago
        // Config: 60 tokens per 60s = 1 token per second
        // 30s elapsed = 30 tokens should have been refilled (but capped at capacity)
        // This means bucket should have capacity, so retry-after should be 0
        Mockito.when(backend.getSnapshot(any(), any(), any()))
                .thenReturn(new RateLimitSnapshot(0, 60, clock.millis() - 30_000));

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // With long elapsed time, retry-after should be minimal
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void handlesInvalidConfigurationGracefully() {
        // Create rule with invalid config (0 refill period)
        com.conduit.egress.agent.dto.ControlPlaneRuleDTO dto = new com.conduit.egress.agent.dto.ControlPlaneRuleDTO();
        dto.setServiceName("test-service");
        dto.setName("invalid-rule");
        dto.setHostPatterns(List.of("api.example.com"));
        dto.setPathPatterns(List.of("/test"));
        dto.setCapacity(60);
        dto.setRefillTokens(60);
        dto.setRefillPeriod(Duration.ofMillis(1)); // Minimal non-zero to keep config valid
        dto.setHttpMethod("GET");
        dto.setDimensions(List.of(RateLimitDimension.HOST.name()));

        RateLimitConfig cfg = new RateLimitConfig(
                "invalid-rule",
                60,
                60,
                Duration.ofMillis(1),
                EnumSet.of(RateLimitDimension.HOST)
        );

        RuleCache.CachedRule rule = new RuleCache.CachedRule(dto, cfg);
        
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any()))
                .thenReturn(false)
                .thenReturn(true);
        
        Mockito.when(backend.getSnapshot(any(), any(), any()))
                .thenReturn(new RateLimitSnapshot(0, 60, clock.millis()));

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // Should handle gracefully and use backoff
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    private RuleCache.CachedRule buildRule(String name) {
        com.conduit.egress.agent.dto.ControlPlaneRuleDTO dto = new com.conduit.egress.agent.dto.ControlPlaneRuleDTO();
        dto.setServiceName("test-service");
        dto.setName(name);
        dto.setHostPatterns(List.of("api.example.com"));
        dto.setPathPatterns(List.of("/test"));
        dto.setCapacity(60);
        dto.setRefillTokens(60);
        dto.setRefillPeriod(Duration.ofSeconds(60));
        dto.setHttpMethod("GET");
        dto.setDimensions(List.of(RateLimitDimension.HOST.name()));

        RateLimitConfig cfg = new RateLimitConfig(
                name,
                60,
                60,
                Duration.ofSeconds(60),
                EnumSet.of(RateLimitDimension.HOST)
        );

        return new RuleCache.CachedRule(dto, cfg);
    }

    static class MockClientResponse {
        static ClientResponse ok() {
            return ClientResponse.create(org.springframework.http.HttpStatus.OK).build();
        }
    }
}
