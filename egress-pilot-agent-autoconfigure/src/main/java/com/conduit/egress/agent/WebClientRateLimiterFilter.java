package com.conduit.egress.agent;

import com.conduit.egress.core.RateLimitBackend;
import com.conduit.egress.core.RateLimitConfig;
import com.conduit.egress.core.RateLimitDimension;
import com.conduit.egress.core.RateLimitExceededException;
import com.conduit.egress.core.RateLimitKey;
import com.conduit.egress.core.RateLimitSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

public class WebClientRateLimiterFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(WebClientRateLimiterFilter.class);

    private final RuleCache ruleCache;
    private final RateLimitBackend backend;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final EgressAgentProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public WebClientRateLimiterFilter(RuleCache ruleCache,
                                      RateLimitBackend backend,
                                      Clock clock,
                                      MeterRegistry meterRegistry,
                                      EgressAgentProperties properties) {
        this.ruleCache = ruleCache;
        this.backend = backend;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        URI uri = request.url();
        HttpMethod method = request.method();
        String host = uri.getHost();
        String path = uri.getPath();

        RuleCache.CachedRule matched = findMatchingRule(host, path, method);
        if (matched == null) {
            return next.exchange(request);
        }

        RateLimitConfig cfg = matched.getConfig();
        RateLimitKey key = buildKey(matched, host, path, method, request);

        EgressAgentProperties.BehaviorOnExhaustion behavior = properties.getBehaviorOnExhaustion();
        switch (behavior) {
            case QUEUE:
                return applyQueueBehavior(request, next, cfg, key);
            case SMOOTH_FLOW:
                return applySmoothFlowBehavior(request, next, cfg, key);
            case BLOCK:
            default:
                return applyBlockBehavior(request, next, cfg, key);
        }
    }

    private Mono<ClientResponse> applyBlockBehavior(ClientRequest request,
                                                    ExchangeFunction next,
                                                    RateLimitConfig cfg,
                                                    RateLimitKey key) {
        boolean allowed = tryAcquireOrFailOpen(key, 1, cfg);
        if (!allowed) {
            long retryAfter = estimateRetryAfterMillis(key, cfg);
            meterRegistry.counter("conduit.egress.agent.denied").increment();
            log.debug("Rate limit exhausted for key={}, behavior=BLOCK, retryAfterMs={}", key, retryAfter);
            return Mono.error(new RateLimitExceededException(
                    key,
                    "Rate limit exceeded (BLOCK mode)",
                    retryAfter
            ));
        }

        meterRegistry.counter("conduit.egress.agent.allowed").increment();
        return next.exchange(request);
    }

    private Mono<ClientResponse> applyQueueBehavior(ClientRequest request,
                                                    ExchangeFunction next,
                                                    RateLimitConfig cfg,
                                                    RateLimitKey key) {
        long backoffMs = properties.getQueue().getBackoffMs();
        long maxWaitMs = properties.getQueue().getMaxWaitMs();
        Instant enqueueTime = clock.instant();

        return Mono.defer(() -> {
            long waited = Duration.between(enqueueTime, clock.instant()).toMillis();
            if (waited > maxWaitMs) {
                meterRegistry.counter("conduit.egress.agent.queue.dropped").increment();
                return Mono.error(new RateLimitExceededException(
                        key,
                        "Queued request expired while waiting for capacity",
                        estimateRetryAfterMillis(key, cfg)
                ));
            }

            boolean allowed = tryAcquireOrFailOpen(key, 1, cfg);
            if (allowed) {
                meterRegistry.counter("conduit.egress.agent.allowed").increment();
                return next.exchange(request);
            }

            long retryAfter = estimateRetryAfterMillis(key, cfg);
            long delay = Math.max(backoffMs, retryAfter);

            log.debug("Rate limit exhausted for key={}, behavior=QUEUE, delayMs={} (retryAfterMs={})",
                    key, delay, retryAfter);

            meterRegistry.counter("conduit.egress.agent.queued").increment();
            return Mono.delay(Duration.ofMillis(delay))
                    .then(applyQueueBehavior(request, next, cfg, key));
        });
    }

    private Mono<ClientResponse> applySmoothFlowBehavior(ClientRequest request,
                                                         ExchangeFunction next,
                                                         RateLimitConfig cfg,
                                                         RateLimitKey key) {
        long intervalMs = properties.getSmooth().getIntervalMs();

        log.trace("Applying smooth-flow delay {}ms for key={}", intervalMs, key);

        return Mono.delay(Duration.ofMillis(intervalMs))
                .flatMap(t -> {
                    boolean allowed = tryAcquireOrFailOpen(key, 1, cfg);
                    if (!allowed) {
                        long retryAfter = estimateRetryAfterMillis(key, cfg);
                        meterRegistry.counter("conduit.egress.agent.denied").increment();
                        log.debug("Rate limit exhausted for key={}, behavior=SMOOTH_FLOW, retryAfterMs={}",
                                key, retryAfter);
                        return Mono.error(new RateLimitExceededException(
                                key,
                                "Rate limit exceeded (SMOOTH_FLOW mode)",
                                retryAfter
                        ));
                    }
                    meterRegistry.counter("conduit.egress.agent.allowed").increment();
                    return next.exchange(request);
                });
    }

    private boolean tryAcquireOrFailOpen(RateLimitKey key, long permits, RateLimitConfig cfg) {
        try {
            return backend.tryAcquire(key, permits, cfg, clock);
        } catch (Exception ex) {
            meterRegistry.counter("conduit.egress.agent.backend.error").increment();
            if (properties.isFailOpen()) {
                log.warn("Backend failure while acquiring rate limit for key={}, fail-open=true, allowing request", key, ex);
                return true;
            }
            log.warn("Backend failure while acquiring rate limit for key={}, fail-open=false, rejecting request", key, ex);
            throw ex;
        }
    }

    private long estimateRetryAfterMillis(RateLimitKey key, RateLimitConfig cfg) {
        try {
            RateLimitSnapshot snapshot = backend.getSnapshot(key, cfg, clock);
            long remaining = snapshot.getRemainingTokens();
            if (remaining > 0) {
                return 0L;
            }
            long capacity = cfg.getCapacity();
            long refillTokens = cfg.getRefillTokens();
            long periodMs = cfg.getRefillPeriod().toMillis();
            if (refillTokens <= 0 || periodMs <= 0 || capacity <= 0) {
                return periodMs;
            }

            long nowMs = clock.instant().toEpochMilli();
            long lastRefillMs = snapshot.getLastRefillEpochMillis();
            long elapsed = Math.max(0L, nowMs - lastRefillMs);

            double tokensPerMs = (double) refillTokens / (double) periodMs;
            long regained = (long) (elapsed * tokensPerMs);

            long deficit = 1L - regained;
            if (deficit <= 0) {
                return 0L;
            }

            long msPerToken = (long) Math.ceil(1.0d / tokensPerMs);
            return deficit * msPerToken;
        } catch (Exception ex) {
            log.debug("Failed to estimate retry-after for key={}, using default 1000ms", key, ex);
            return 1_000L;
        }
    }

    private RuleCache.CachedRule findMatchingRule(String host, String path, HttpMethod method) {
        List<RuleCache.CachedRule> rules = ruleCache.getRules();
        for (RuleCache.CachedRule rule : rules) {
            if (!matchesHost(rule.getDto().getHostPatterns(), host)) {
                continue;
            }
            if (!matchesPath(rule.getDto().getPathPatterns(), path)) {
                continue;
            }
            String configuredMethod = rule.getDto().getHttpMethod();
            if (configuredMethod != null && !configuredMethod.isBlank()
                    && method != null
                    && !configuredMethod.equalsIgnoreCase("ANY")
                    && !configuredMethod.equalsIgnoreCase(method.name())) {
                continue;
            }
            return rule;
        }
        return null;
    }

    private boolean matchesHost(List<String> hostPatterns, String host) {
        if (hostPatterns == null || hostPatterns.isEmpty()) {
            return true;
        }
        for (String pattern : hostPatterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if ("*".equals(pattern) || (host != null && pattern.equalsIgnoreCase(host))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPath(List<String> pathPatterns, String path) {
        if (pathPatterns == null || pathPatterns.isEmpty()) {
            return true;
        }
        for (String pattern : pathPatterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private RateLimitKey buildKey(RuleCache.CachedRule rule,
                                  String host,
                                  String path,
                                  HttpMethod method,
                                  ClientRequest request) {

        EnumSet<RateLimitDimension> dims = rule.getConfig().getDimensions();
        RateLimitKey.Builder builder = RateLimitKey.builder(rule.getConfig().getName());

        if (dims.contains(RateLimitDimension.HOST)) {
            builder.host(host);
        }
        if (dims.contains(RateLimitDimension.PATH)) {
            builder.path(path);
        }
        if (dims.contains(RateLimitDimension.METHOD) && method != null) {
            builder.method(method.name());
        }
        if (dims.contains(RateLimitDimension.PACKAGE)) {
            builder.pkg(request.attribute("egress.caller.package").map(Object::toString).orElse(null));
        }
        if (dims.contains(RateLimitDimension.PRINCIPAL)) {
            builder.principal(request.attribute("egress.caller.principal").map(Object::toString).orElse(null));
        }
        if (dims.contains(RateLimitDimension.API_KEY)) {
            builder.apiKey(request.attribute("egress.caller.api-key").map(Object::toString).orElse(null));
        }

        return builder.build();
    }
}
