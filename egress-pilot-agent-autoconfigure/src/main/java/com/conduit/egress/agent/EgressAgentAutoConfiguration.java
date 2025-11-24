package com.conduit.egress.agent;

import com.conduit.egress.core.InMemoryTokenBucketBackend;
import com.conduit.egress.core.RateLimitBackend;
import com.conduit.egress.redis.RedisTokenBucketBackend;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;

import java.net.URI;
import java.time.Clock;

@AutoConfiguration
@EnableConfigurationProperties(EgressAgentProperties.class)
@ConditionalOnClass(WebClient.class)
@ConditionalOnProperty(prefix = "conduit.egress.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class EgressAgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock egressAgentClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(name = "egressAgentRedisTemplate")
    public StringRedisTemplate egressAgentRedisTemplate(EgressAgentProperties properties) {
        URI uri = URI.create(properties.getRedisUri());
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration();
        cfg.setHostName(uri.getHost());
        cfg.setPort(uri.getPort() == -1 ? 6379 : uri.getPort());
        if (uri.getUserInfo() != null && uri.getUserInfo().contains(":")) {
            String password = uri.getUserInfo().split(":", 2)[1];
            cfg.setPassword(password);
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(cfg);
        factory.afterPropertiesSet();
        return new StringRedisTemplate(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitBackend egressRateLimitBackend(
            EgressAgentProperties properties,
            StringRedisTemplate egressAgentRedisTemplate
    ) {
        if ("redis".equalsIgnoreCase(properties.getBackend())
                || "dragonfly".equalsIgnoreCase(properties.getBackend())) {
            return new RedisTokenBucketBackend(egressAgentRedisTemplate);
        }
        return new InMemoryTokenBucketBackend();
    }

    @Bean
    public ControlPlaneClient controlPlaneClient(EgressAgentProperties properties) {
        return new ControlPlaneClient(properties);
    }

    @Bean
    public RuleCache ruleCache(ControlPlaneClient client, MeterRegistry meterRegistry) {
        return new RuleCache(client, meterRegistry);
    }

    @Bean
    public WebClientRateLimiterFilter webClientRateLimiterFilter(
            RuleCache ruleCache,
            RateLimitBackend backend,
            Clock egressAgentClock,
            MeterRegistry meterRegistry,
            EgressAgentProperties properties
    ) {
        return new WebClientRateLimiterFilter(ruleCache, backend, egressAgentClock, meterRegistry, properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "conduitWebClientCustomizer")
    public WebClientCustomizer conduitWebClientCustomizer(ExchangeFilterFunction webClientRateLimiterFilter) {
        return builder -> builder.filter(webClientRateLimiterFilter);
    }

    @Bean
    public EgressRateLimitAspect egressRateLimitAspect(
            RuleCache ruleCache,
            RateLimitBackend backend,
            Clock egressAgentClock,
            MeterRegistry meterRegistry,
            EgressAgentProperties properties
    ) {
        return new EgressRateLimitAspect(ruleCache, backend, egressAgentClock, meterRegistry, properties);
    }

    @Bean
    public RateLimitBackendHealthIndicator rateLimitBackendHealthIndicator(
            RateLimitBackend backend,
            Clock egressAgentClock
    ) {
        return new RateLimitBackendHealthIndicator(backend, egressAgentClock);
    }
}
