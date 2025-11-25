package com.conduit.egress.controlplane.web;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public class RateLimitRuleDto {

    private Long id;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "must use letters, numbers, dots, dashes, or underscores")
    private String serviceName;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "must use letters, numbers, dots, dashes, or underscores")
    private String name;

    @JsonAlias("hostPattern")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @NotEmpty
    private List<@NotBlank String> hostPatterns;

    @JsonAlias("pathPattern")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @NotEmpty
    private List<@NotBlank String> pathPatterns;

    @Pattern(regexp = "^[A-Z]+$", message = "must be uppercase HTTP method when provided")
    private String httpMethod;

    @Min(1)
    private long capacity;

    @Min(1)
    private long refillTokens;

    @Min(1)
    private long refillPeriodSeconds;

    private List<@Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "must use letters, numbers, dots, dashes, or underscores") String> dimensions;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public long getRefillPeriodSeconds() {
        return refillPeriodSeconds;
    }

    public void setRefillPeriodSeconds(long refillPeriodSeconds) {
        this.refillPeriodSeconds = refillPeriodSeconds;
    }

    public List<String> getDimensions() {
        return dimensions;
    }

    public void setDimensions(List<String> dimensions) {
        this.dimensions = dimensions;
    }
}
