package com.conduit.egress.agent;

import com.conduit.egress.core.RateLimitBackend;
import com.conduit.egress.core.RateLimitConfig;
import com.conduit.egress.core.RateLimitDimension;
import com.conduit.egress.core.RateLimitExceededException;
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

class BehaviorSwitchingTests {

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

    private RuleCache ruleCache;
    private RateLimitBackend backend;
    private Clock clock;
    private SimpleMeterRegistry registry;
    private EgressAgentProperties properties;

    @BeforeEach
    void setUp() {
        ruleCache = Mockito.mock(RuleCache.class);
        backend = Mockito.mock(RateLimitBackend.class);
        clock = new FixedClock(Instant.now());
        registry = new SimpleMeterRegistry();
        properties = new EgressAgentProperties();
    }

    @Test
    void blockBehaviorDeniesImmediately() {
        properties.setBehaviorOnExhaustion(EgressAgentProperties.BehaviorOnExhaustion.BLOCK);
        WebClientRateLimiterFilter filter = new WebClientRateLimiterFilter(ruleCache, backend, clock, registry, properties);

        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(false);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // BLOCK mode: immediate error
        StepVerifier.create(result)
                .expectError(RateLimitExceededException.class)
                .verify();

        double denied = registry.get("conduit.egress.agent.denied").counter().count();
        assert denied == 1.0;

        // Should NOT have queued metric in BLOCK mode
        assert !registry.getMeters().stream()
                .anyMatch(m -> m.getId().getName().equals("conduit.egress.agent.queued"));
    }

    @Test
    void queueBehaviorRetriesAfterDelay() {
        properties.setBehaviorOnExhaustion(EgressAgentProperties.BehaviorOnExhaustion.QUEUE);
        properties.getQueue().setBackoffMs(50L);
        WebClientRateLimiterFilter filter = new WebClientRateLimiterFilter(ruleCache, backend, clock, registry, properties);

        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        
        // Denied first, allowed second
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any()))
                .thenReturn(false)
                .thenReturn(true);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // QUEUE mode: delayed success
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        double queued = registry.get("conduit.egress.agent.queued").counter().count();
        double allowed = registry.get("conduit.egress.agent.allowed").counter().count();
        assert queued == 1.0;
        assert allowed == 1.0;
    }

    @Test
    void smoothFlowBehaviorAlwaysDelays() {
        properties.setBehaviorOnExhaustion(EgressAgentProperties.BehaviorOnExhaustion.SMOOTH_FLOW);
        properties.getSmooth().setIntervalMs(30L);
        WebClientRateLimiterFilter filter = new WebClientRateLimiterFilter(ruleCache, backend, clock, registry, properties);

        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(true);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // SMOOTH_FLOW mode: delayed success even with capacity
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        double allowed = registry.get("conduit.egress.agent.allowed").counter().count();
        assert allowed == 1.0;

        // Should NOT have queued metric in SMOOTH_FLOW mode
        assert !registry.getMeters().stream()
                .anyMatch(m -> m.getId().getName().equals("conduit.egress.agent.queued"));
    }

    @Test
    void behaviorCanBeSwitchedAtRuntime() {
        // Start with BLOCK
        properties.setBehaviorOnExhaustion(EgressAgentProperties.BehaviorOnExhaustion.BLOCK);
        WebClientRateLimiterFilter filter = new WebClientRateLimiterFilter(ruleCache, backend, clock, registry, properties);

        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(false);

        ClientRequest request1 = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        // First call with BLOCK behavior
        Mono<ClientResponse> result1 = filter.filter(request1, next);
        StepVerifier.create(result1)
                .expectError(RateLimitExceededException.class)
                .verify();

        // Switch to QUEUE
        properties.setBehaviorOnExhaustion(EgressAgentProperties.BehaviorOnExhaustion.QUEUE);
        
        // Reset mock for second attempt
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any()))
                .thenReturn(false)
                .thenReturn(true);

        ClientRequest request2 = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();

        // Second call with QUEUE behavior
        Mono<ClientResponse> result2 = filter.filter(request2, next);
        StepVerifier.create(result2)
                .expectNextCount(1)
                .verifyComplete();

        // Should have both denied (from BLOCK) and queued (from QUEUE)
        double denied = registry.get("conduit.egress.agent.denied").counter().count();
        double queued = registry.get("conduit.egress.agent.queued").counter().count();
        assert denied == 1.0;
        assert queued == 1.0;
    }

    @Test
    void defaultBehaviorIsBlock() {
        // Don't set behavior explicitly
        WebClientRateLimiterFilter filter = new WebClientRateLimiterFilter(ruleCache, backend, clock, registry, properties);

        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(false);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // Default should be BLOCK
        StepVerifier.create(result)
                .expectError(RateLimitExceededException.class)
                .verify();
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
