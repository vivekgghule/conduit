package com.conduit.egress.agent;

import com.conduit.egress.agent.dto.ControlPlaneRuleDTO;
import com.conduit.egress.core.RateLimitConfig;
import com.conduit.egress.core.RateLimitDimension;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory rule cache refreshed periodically from control plane.
 */
public class RuleCache {

    private static final Logger log = LoggerFactory.getLogger(RuleCache.class);

    public static final class CachedRule {
        private final ControlPlaneRuleDTO dto;
        private final RateLimitConfig config;

        CachedRule(ControlPlaneRuleDTO dto, RateLimitConfig config) {
            this.dto = dto;
            this.config = config;
        }

        public ControlPlaneRuleDTO getDto() {
            return dto;
        }

        public RateLimitConfig getConfig() {
            return config;
        }
    }

    private final ControlPlaneClient client;
    private final MeterRegistry meterRegistry;
    private final AtomicReference<List<CachedRule>> currentRules = new AtomicReference<>(List.of());
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public RuleCache(ControlPlaneClient client, MeterRegistry meterRegistry) {
        this.client = client;
        this.meterRegistry = meterRegistry;
    }

    public List<CachedRule> getRules() {
        return currentRules.get();
    }

    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "PT10S")
    public void refresh() {
        int failures = consecutiveFailures.get();
        long backoffSeconds = Math.min(60, (long) Math.pow(2, failures));
        if (failures > 0) {
            log.warn("Rule refresh backoff active (failures={}, backoff={}s)", failures, backoffSeconds);
            meterRegistry.counter("conduit.egress.agent.rule_refresh_backoff").increment();
            return;
        }

        Timer.Sample sample = Timer.start();
        Flux<ControlPlaneRuleDTO> flux = client.fetchRules();
        flux.collectList().subscribe(list -> {
            List<CachedRule> next = new ArrayList<>();
            for (ControlPlaneRuleDTO dto : list) {
                EnumSet<RateLimitDimension> dims = EnumSet.noneOf(RateLimitDimension.class);
                if (dto.getDimensions() != null) {
                    for (String d : dto.getDimensions()) {
                        dims.add(RateLimitDimension.valueOf(d));
                    }
                } else {
                    dims = EnumSet.of(RateLimitDimension.HOST, RateLimitDimension.PATH);
                }
                RateLimitConfig config = new RateLimitConfig(
                        dto.getName(),
                        dto.getCapacity(),
                        dto.getRefillTokens(),
                        dto.getRefillPeriod(),
                        dims
                );
                next.add(new CachedRule(dto, config));
            }
            currentRules.set(Collections.unmodifiableList(next));
            consecutiveFailures.set(0);
            sample.stop(meterRegistry.timer("conduit.egress.agent.rule_refresh", "outcome", "success"));
            log.info("Updated egress rules from control-plane: {}", next.size());
        }, error -> {
            consecutiveFailures.incrementAndGet();
            sample.stop(meterRegistry.timer("conduit.egress.agent.rule_refresh", "outcome", "error"));
            meterRegistry.counter("conduit.egress.agent.rule_refresh_error").increment();
            log.error("Failed to refresh rules from control-plane", error);
        });
    }
}
