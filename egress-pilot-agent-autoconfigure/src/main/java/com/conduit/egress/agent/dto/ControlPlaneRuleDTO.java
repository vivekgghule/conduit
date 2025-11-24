package com.conduit.egress.agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.List;

public class ControlPlaneRuleDTO {

    private String id;
    private String serviceName;
    private String name;
    @JsonAlias("hostPattern")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> hostPatterns;
    @JsonAlias("pathPattern")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> pathPatterns;
    private String httpMethod;
    private long capacity;
    private long refillTokens;
    private Duration refillPeriod;
    private Long refillPeriodSeconds;
    private List<String> dimensions;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getHostPatterns() {
        return hostPatterns;
    }

    public void setHostPatterns(List<String> hostPatterns) {
        this.hostPatterns = hostPatterns;
    }

    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    public void setPathPatterns(List<String> pathPatterns) {
        this.pathPatterns = pathPatterns;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public long getRefillTokens() {
        return refillTokens;
    }

    public void setRefillTokens(long refillTokens) {
        this.refillTokens = refillTokens;
    }

    public Duration getRefillPeriod() {
        if (refillPeriod != null) {
            return refillPeriod;
        }
        if (refillPeriodSeconds != null) {
            return Duration.ofSeconds(refillPeriodSeconds);
        }
        return null;
    }

    public void setRefillPeriod(Duration refillPeriod) {
        this.refillPeriod = refillPeriod;
    }

    public Long getRefillPeriodSeconds() {
        return refillPeriodSeconds;
    }

    @JsonProperty("refillPeriodSeconds")
    public void setRefillPeriodSeconds(Long refillPeriodSeconds) {
        this.refillPeriodSeconds = refillPeriodSeconds;
        if (refillPeriodSeconds != null) {
            this.refillPeriod = Duration.ofSeconds(refillPeriodSeconds);
        }
    }

    public List<String> getDimensions() {
        return dimensions;
    }

    public void setDimensions(List<String> dimensions) {
        this.dimensions = dimensions;
    }
}
