package com.conduit.egress.controlplane.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "egress_rate_limit_rules",
        uniqueConstraints = @UniqueConstraint(columnNames = {"service_name", "name"})
)
public class RateLimitRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "egress_rate_limit_rule_hosts", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "host_pattern", nullable = false)
    private List<String> hostPatterns = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "egress_rate_limit_rule_paths", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "path_pattern", nullable = false)
    private List<String> pathPatterns = new ArrayList<>();

    private String httpMethod;

    @Min(1)
    private long capacity;

    @Min(1)
    private long refillTokens;

    @Min(1)
    private long refillPeriodSeconds;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "egress_rate_limit_rule_dimensions", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "dimension")
    private List<String> dimensions;

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
        this.hostPatterns = hostPatterns == null ? new ArrayList<>() : hostPatterns;
    }

    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    public void setPathPatterns(List<String> pathPatterns) {
        this.pathPatterns = pathPatterns == null ? new ArrayList<>() : pathPatterns;
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
