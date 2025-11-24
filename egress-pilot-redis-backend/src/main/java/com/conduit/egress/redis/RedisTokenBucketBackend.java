package com.conduit.egress.redis;

import com.conduit.egress.core.RateLimitBackend;
import com.conduit.egress.core.RateLimitConfig;
import com.conduit.egress.core.RateLimitKey;
import com.conduit.egress.core.RateLimitSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Redis / Dragonfly-based token bucket using a Lua script for atomic updates.
 */
public class RedisTokenBucketBackend implements RateLimitBackend {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketBackend.class);

    private static final String SCRIPT_TEXT =
            "local key = KEYS[1]\n" +
            "local capacity = tonumber(ARGV[1])\n" +
            "local refill_tokens = tonumber(ARGV[2])\n" +
            "local refill_interval_ms = tonumber(ARGV[3])\n" +
            "local now_ms = tonumber(ARGV[4])\n" +
            "local requested = tonumber(ARGV[5])\n" +
            "local state = redis.call('HMGET', key, 'tokens', 'last_refill')\n" +
            "local tokens = tonumber(state[1])\n" +
            "local last_refill = tonumber(state[2])\n" +
            "if tokens == nil then\n" +
            "  tokens = capacity\n" +
            "  last_refill = now_ms\n" +
            "else\n" +
            "  local elapsed = now_ms - last_refill\n" +
            "  if elapsed > 0 then\n" +
            "    local periods = math.floor(elapsed / refill_interval_ms)\n" +
            "    if periods > 0 then\n" +
            "      local to_add = periods * refill_tokens\n" +
            "      tokens = math.min(capacity, tokens + to_add)\n" +
            "      last_refill = last_refill + periods * refill_interval_ms\n" +
            "    end\n" +
            "  end\n" +
            "end\n" +
            "local allowed = 0\n" +
            "if requested > 0 and tokens >= requested then\n" +
            "  tokens = tokens - requested\n" +
            "  allowed = 1\n" +
            "end\n" +
            "redis.call('HSET', key, 'tokens', tokens, 'last_refill', last_refill)\n" +
            "redis.call('PEXPIRE', key, refill_interval_ms * 3)\n" +
            "return { allowed, tokens, capacity, last_refill }\n";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> script;
    private final String keyPrefix;

    public RedisTokenBucketBackend(StringRedisTemplate redisTemplate) {
        this(redisTemplate, "egress:bucket:");
    }

    public RedisTokenBucketBackend(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.keyPrefix = keyPrefix;
        this.script = new DefaultRedisScript<>(SCRIPT_TEXT, List.class);
    }

    private String toRedisKey(RateLimitKey key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.toString().getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return keyPrefix + encoded;
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available, falling back to plain key", e);
            return keyPrefix + key.toString();
        }
    }

    @Override
    public boolean tryAcquire(RateLimitKey key, long permits, RateLimitConfig config, Clock clock) {
        if (permits <= 0) {
            return true;
        }
        long nowMs = clock.millis();
        List<String> keys = Collections.singletonList(toRedisKey(key));
        Duration period = config.getRefillPeriod();
        List<String> args = List.of(
                Long.toString(config.getCapacity()),
                Long.toString(config.getRefillTokens()),
                Long.toString(period.toMillis()),
                Long.toString(nowMs),
                Long.toString(permits)
        );

        @SuppressWarnings("unchecked")
        List<Long> result = (List<Long>) redisTemplate.execute(script, keys, args.toArray());
        if (result == null || result.isEmpty()) {
            log.error("Redis script returned null/empty result for key {}", key);
            return false;
        }
        Long allowed = result.get(0);
        return allowed != null && allowed == 1L;
    }

    @Override
    public RateLimitSnapshot getSnapshot(RateLimitKey key, RateLimitConfig config, Clock clock) {
        String redisKey = toRedisKey(key);
        List<Object> values = redisTemplate.opsForHash().multiGet(redisKey, List.of("tokens", "last_refill"));
        if (values == null || values.get(0) == null || values.get(1) == null) {
            return new RateLimitSnapshot(config.getCapacity(), config.getCapacity(), clock.millis());
        }
        long tokens = Long.parseLong(values.get(0).toString());
        long lastRefill = Long.parseLong(values.get(1).toString());
        return new RateLimitSnapshot(tokens, config.getCapacity(), lastRefill);
    }
}
