package com.conduit.egress.agent;

import com.conduit.egress.core.RateLimitBackend;
import com.conduit.egress.core.RateLimitConfig;
import com.conduit.egress.core.RateLimitDimension;
import com.conduit.egress.core.RateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.*;
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

class WebClientRateLimiterFilterTests {

    static class FixedClock extends Clock {
        private final Instant instant;

        FixedClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() { return ZoneId.systemDefault(); }

        @Override
        public Clock withZone(ZoneId zone) { return this; }

        @Override
        public Instant instant() { return instant; }
    }

    @Test
    void allowsWhenBackendApproves() {
        RuleCache.CachedRule rule = buildRule("github-api");
        RuleCache cache = Mockito.mock(RuleCache.class);
        Mockito.when(cache.getRules()).thenReturn(List.of(rule));

        RateLimitBackend backend = Mockito.mock(RateLimitBackend.class);
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(true);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EgressAgentProperties props = new EgressAgentProperties();
        Clock clock = new FixedClock(Instant.now());

        WebClientRateLimiterFilter filter =
                new WebClientRateLimiterFilter(cache, backend, clock, registry, props);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/rate_limit")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void deniesWhenBackendRejects() {
        RuleCache.CachedRule rule = buildRule("github-api");
        RuleCache cache = Mockito.mock(RuleCache.class);
        Mockito.when(cache.getRules()).thenReturn(List.of(rule));

        RateLimitBackend backend = Mockito.mock(RateLimitBackend.class);
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(false);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EgressAgentProperties props = new EgressAgentProperties();
        Clock clock = new FixedClock(Instant.now());

        WebClientRateLimiterFilter filter =
                new WebClientRateLimiterFilter(cache, backend, clock, registry, props);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/rate_limit")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        StepVerifier.create(result)
                .expectError(RateLimitExceededException.class)
                .verify();
    }

    @Test
    void matchesSecondaryHostAndPathPatterns() {
        RuleCache.CachedRule rule = buildRule(
                "github-api",
                List.of("status.github.com", "api.github.com"),
                List.of("/status", "/rate_limit")
        );
        RuleCache cache = Mockito.mock(RuleCache.class);
        Mockito.when(cache.getRules()).thenReturn(List.of(rule));

        RateLimitBackend backend = Mockito.mock(RateLimitBackend.class);
        Mockito.when(backend.tryAcquire(any(), anyLong(), any(), any())).thenReturn(true);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EgressAgentProperties props = new EgressAgentProperties();
        Clock clock = new FixedClock(Instant.now());

        WebClientRateLimiterFilter filter =
                new WebClientRateLimiterFilter(cache, backend, clock, registry, props);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/rate_limit")).build();
        ExchangeFunction next = r -> Mono.just(MockClientResponse.ok());

        Mono<ClientResponse> result = filter.filter(request, next);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    private RuleCache.CachedRule buildRule(String name) {
        return buildRule(name, List.of("api.github.com"), List.of("/rate_limit"));
    }

    private RuleCache.CachedRule buildRule(String name, List<String> hostPatterns, List<String> pathPatterns) {
        com.conduit.egress.agent.dto.ControlPlaneRuleDTO dto = new com.conduit.egress.agent.dto.ControlPlaneRuleDTO();
        dto.setServiceName("sample-client");
        dto.setName(name);
        dto.setHostPatterns(hostPatterns);
        dto.setPathPatterns(pathPatterns);
        dto.setCapacity(60);
        dto.setRefillTokens(60);
        dto.setRefillPeriod(Duration.ofSeconds(60));
        dto.setHttpMethod("GET");
        dto.setDimensions(List.of(RateLimitDimension.HOST.name(), RateLimitDimension.PATH.name(), RateLimitDimension.METHOD.name()));

        RateLimitConfig cfg = new RateLimitConfig(
                name,
                60,
                60,
                Duration.ofSeconds(60),
                EnumSet.of(RateLimitDimension.HOST, RateLimitDimension.PATH, RateLimitDimension.METHOD)
        );

        return new RuleCache.CachedRule(dto, cfg);
    }

    static class MockClientResponse {

        static ClientResponse ok() {
            return ClientResponse.create(org.springframework.http.HttpStatus.OK).build();
        }
    }
}
