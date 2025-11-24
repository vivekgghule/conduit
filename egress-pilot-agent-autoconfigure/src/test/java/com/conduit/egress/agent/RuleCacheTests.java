package com.conduit.egress.agent;

import com.conduit.egress.agent.dto.ControlPlaneRuleDTO;
import com.conduit.egress.core.RateLimitDimension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleCacheTests {

    @Test
    void refreshReplacesCurrentRulesOnSuccess() {
        ControlPlaneClient client = org.mockito.Mockito.mock(ControlPlaneClient.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        ControlPlaneRuleDTO dto = new ControlPlaneRuleDTO();
        dto.setServiceName("sample-client");
        dto.setName("github-api");
        dto.setHostPatterns(List.of("api.github.com"));
        dto.setPathPatterns(List.of("/rate_limit"));
        dto.setCapacity(60);
        dto.setRefillTokens(60);
        dto.setRefillPeriod(Duration.ofSeconds(60));
        dto.setDimensions(List.of(RateLimitDimension.HOST.name(), RateLimitDimension.PATH.name()));

        org.mockito.Mockito.when(client.fetchRules()).thenReturn(Flux.just(dto));

        RuleCache cache = new RuleCache(client, registry);

        cache.refresh();

        List<RuleCache.CachedRule> rules = cache.getRules();
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getConfig().getName()).isEqualTo("github-api");
    }

    @Test
    void refreshIncrementsErrorMetricOnFailure() {
        ControlPlaneClient client = org.mockito.Mockito.mock(ControlPlaneClient.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        org.mockito.Mockito.when(client.fetchRules())
                .thenReturn(Flux.error(new IllegalStateException("boom")));

        RuleCache cache = new RuleCache(client, registry);

        cache.refresh();

        double errors = registry.get("conduit.egress.agent.rule_refresh_error").counter().count();
        assertThat(errors).isEqualTo(1.0d);
    }
}
