package com.conduit.egress.agent;

import com.conduit.egress.core.RateLimitBackend;
import com.conduit.egress.core.RateLimitConfig;
import com.conduit.egress.core.RateLimitExceededException;
import com.conduit.egress.core.RateLimitKey;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Clock;

@Aspect
public class EgressRateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(EgressRateLimitAspect.class);

    private final RuleCache ruleCache;
    private final RateLimitBackend backend;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final EgressAgentProperties properties;

    public EgressRateLimitAspect(
            RuleCache ruleCache,
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

    @Around("@annotation(com.conduit.egress.agent.EgressRateLimited)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        EgressRateLimited ann = method.getAnnotation(EgressRateLimited.class);
        String ruleName = ann.value();

        RuleCache.CachedRule rule = ruleCache.getRules().stream()
                .filter(r -> r.getConfig().getName().equals(ruleName))
                .findFirst()
                .orElse(null);

        if (rule == null) {
            return pjp.proceed();
        }

        RateLimitConfig config = rule.getConfig();
        RateLimitKey key = RateLimitKey.builder(config.getName())
                .pkg(method.getDeclaringClass().getPackageName())
                .build();

        boolean allowed;
        try {
            allowed = backend.tryAcquire(key, 1, config, clock);
        } catch (Exception ex) {
            meterRegistry.counter("conduit.egress.agent.backend_error", "rule", config.getName()).increment();
            log.error("Backend failure in annotation rate limiter for rule {}", config.getName(), ex);
            if (properties.isFailOpen()) {
                return pjp.proceed();
            }
            throw ex;
        }

        if (!allowed) {
            meterRegistry.counter("conduit.egress.agent.denied", "rule", config.getName()).increment();
            long retryAfterMillis = config.getRefillPeriod().toMillis();
            log.warn("Rate limit exceeded for annotation rule {}", config.getName());
            throw new RateLimitExceededException(key, "Rate limit exceeded", retryAfterMillis);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Object result = pjp.proceed();
            sample.stop(meterRegistry.timer("conduit.egress.agent.invocation", "rule", config.getName(), "outcome", "success"));
            return result;
        } catch (Throwable t) {
            sample.stop(meterRegistry.timer("conduit.egress.agent.invocation", "rule", config.getName(), "outcome", "error"));
            throw t;
        }
    }
}
