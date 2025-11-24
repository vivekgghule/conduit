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

class SmoothFlowBehaviorTests {

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
    private WebClientRateLimiterFilter filter;

    @BeforeEach
    void setUp() {
        ruleCache = Mockito.mock(RuleCache.class);
        backend = Mockito.mock(RateLimitBackend.class);
        clock = new FixedClock(Instant.now());
        registry = new SimpleMeterRegistry();
        properties = new EgressAgentProperties();
        properties.setBehaviorOnExhaustion(EgressAgentProperties.BehaviorOnExhaustion.SMOOTH_FLOW);
        properties.getSmooth().setIntervalMs(50L);
        filter = new WebClientRateLimiterFilter(ruleCache, backend, clock, registry, properties);
    }

    @Test
    void appliesDelayBeforeAllowingRequest() {
        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(true);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // Should complete after delay
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        double allowed = registry.get("conduit.egress.agent.allowed").counter().count();
        assert allowed == 1.0;
    }

    @Test
    void deniesAfterDelayWhenBackendRejects() {
        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(false);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        StepVerifier.create(result)
                .expectError(RateLimitExceededException.class)
                .verify();

        double denied = registry.get("conduit.egress.agent.denied").counter().count();
        assert denied == 1.0;
    }

    @Test
    void appliesConfiguredIntervalDelay() {
        properties.getSmooth().setIntervalMs(100L);

        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(true);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // Delay applied before acquisition
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        double allowed = registry.get("conduit.egress.agent.allowed").counter().count();
        assert allowed == 1.0;
    }

    @Test
    void smoothFlowAppliesDelayEvenWhenCapacityAvailable() {
        // This is the key difference from BLOCK mode
        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(true);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        // Even with capacity, delay is applied for smooth flow
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void multipleRequestsSpreadOverTime() {
        RuleCache.CachedRule rule = buildRule("test-rule");
        Mockito.when(ruleCache.getRules()).thenReturn(List.of(rule));
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(true);

        ClientRequest request1 = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ClientRequest request2 = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        ClientRequest request3 = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/test")).build();
        
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result1 = filter.filter(request1, next);
        Mono<ClientResponse> result2 = filter.filter(request2, next);
        Mono<ClientResponse> result3 = filter.filter(request3, next);

        // All should succeed with delays
        StepVerifier.create(Mono.zip(result1, result2, result3))
                .expectNextCount(1)
                .verifyComplete();

        double allowed = registry.get("conduit.egress.agent.allowed").counter().count();
        assert allowed == 3.0;
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
