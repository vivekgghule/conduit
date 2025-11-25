package com.conduit.egress.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("conduit.egress.agent")
@Validated
public class EgressAgentProperties {

    /**
     * Enables or disables the agent auto-configuration.
     */
    private boolean enabled = true;

    /**
     * Base URL for the control-plane service.
     */
    @NotBlank
    private String controlPlaneBaseUrl = "http://localhost:8090";

    /**
     * API key sent to the control-plane (`X-API-KEY` header).
     */
    @NotBlank
    private String controlPlaneApiKey = "changeme-control-plane-key";

    /**
     * Logical name of the calling service. Used to fetch rules from control-plane.
     */
    @NotBlank
    private String serviceName = "sample-client";

    /**
     * Backend to use for token buckets. Valid values: in-memory, redis, dragonfly.
     */
    @NotBlank
    private String backend = "in-memory";

    /**
     * Redis connection URI, used when backend=redis/dragonfly.
     */
    @NotBlank
    private String redisUri = "redis://localhost:6379";

    /**
     * Redis connection pool settings used when backend=redis/dragonfly.
     */
    @Valid
    private PoolProperties redisPool = new PoolProperties();

    /**
     * Interval in seconds between rule refreshes from control-plane.
     */
    @Min(5)
    private int refreshIntervalSeconds = 10;

    /**
     * If true, requests are allowed when backend fails (Redis/control-plane issues).
     */
    private boolean failOpen = true;

    /**
     * Behavior when rate limit is exhausted.
     */
    private BehaviorOnExhaustion behaviorOnExhaustion = BehaviorOnExhaustion.BLOCK;

    /**
     * Queue configuration, used when behaviorOnExhaustion = QUEUE.
     */
    @Valid
    private QueueProperties queue = new QueueProperties();

    /**
     * Smooth-flow configuration, used when behaviorOnExhaustion = SMOOTH_FLOW.
     */
    @Valid
    private SmoothFlowProperties smooth = new SmoothFlowProperties();

    public enum BehaviorOnExhaustion {
        BLOCK,
        QUEUE,
        SMOOTH_FLOW
    }

    public static class QueueProperties {

        @Min(1)
        private int maxSize = 5000;

        @Min(1)
        private long maxWaitMs = 30_000L;

        @Min(1)
        private long backoffMs = 250L;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public long getMaxWaitMs() {
            return maxWaitMs;
        }

        public void setMaxWaitMs(long maxWaitMs) {
            this.maxWaitMs = maxWaitMs;
        }

        public long getBackoffMs() {
            return backoffMs;
        }

        public void setBackoffMs(long backoffMs) {
            this.backoffMs = backoffMs;
        }
    }

    public static class SmoothFlowProperties {

        @Min(1)
        private long intervalMs = 50L;

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }
    }

    public static class PoolProperties {
        @Min(1)
        private int maxTotal = 8;

        @Min(0)
        private int maxIdle = 8;

        @Min(0)
        private int minIdle = 0;

        @Min(0)
        private long maxWaitMs = 2000;

        public int getMaxTotal() {
            return maxTotal;
        }

        public void setMaxTotal(int maxTotal) {
            this.maxTotal = maxTotal;
        }

        public int getMaxIdle() {
            return maxIdle;
        }

        public void setMaxIdle(int maxIdle) {
            this.maxIdle = maxIdle;
        }

        public int getMinIdle() {
            return minIdle;
        }

        public void setMinIdle(int minIdle) {
            this.minIdle = minIdle;
        }

        public long getMaxWaitMs() {
            return maxWaitMs;
        }

        public void setMaxWaitMs(long maxWaitMs) {
            this.maxWaitMs = maxWaitMs;
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public BehaviorOnExhaustion getBehaviorOnExhaustion() {
        return behaviorOnExhaustion;
    }

    public void setBehaviorOnExhaustion(BehaviorOnExhaustion behaviorOnExhaustion) {
        this.behaviorOnExhaustion = behaviorOnExhaustion;
    }

    public QueueProperties getQueue() {
        return queue;
    }

    public void setQueue(QueueProperties queue) {
        this.queue = queue;
    }

    public SmoothFlowProperties getSmooth() {
        return smooth;
    }

    public void setSmooth(SmoothFlowProperties smooth) {
        this.smooth = smooth;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getControlPlaneBaseUrl() {
        return controlPlaneBaseUrl;
    }

    public void setControlPlaneBaseUrl(String controlPlaneBaseUrl) {
        this.controlPlaneBaseUrl = controlPlaneBaseUrl;
    }

    public String getControlPlaneApiKey() {
        return controlPlaneApiKey;
    }

    public void setControlPlaneApiKey(String controlPlaneApiKey) {
        this.controlPlaneApiKey = controlPlaneApiKey;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public String getRedisUri() {
        return redisUri;
    }

    public void setRedisUri(String redisUri) {
        this.redisUri = redisUri;
    }

    public PoolProperties getRedisPool() {
        return redisPool;
    }

    public void setRedisPool(PoolProperties redisPool) {
        this.redisPool = redisPool;
    }
}
