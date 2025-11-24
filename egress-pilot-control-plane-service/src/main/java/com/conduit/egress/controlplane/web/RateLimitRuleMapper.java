package com.conduit.egress.controlplane.web;

import com.conduit.egress.controlplane.model.RateLimitRuleEntity;

public final class RateLimitRuleMapper {

    private RateLimitRuleMapper() {}

    public static RateLimitRuleDto toDto(RateLimitRuleEntity entity) {
        RateLimitRuleDto dto = new RateLimitRuleDto();
        dto.setId(entity.getId());
        dto.setServiceName(entity.getServiceName());
        dto.setName(entity.getName());
        dto.setHostPatterns(entity.getHostPatterns());
        dto.setPathPatterns(entity.getPathPatterns());
        dto.setHttpMethod(entity.getHttpMethod());
        dto.setCapacity(entity.getCapacity());
        dto.setRefillTokens(entity.getRefillTokens());
        dto.setRefillPeriodSeconds(entity.getRefillPeriodSeconds());
        dto.setDimensions(entity.getDimensions());
        return dto;
    }

    public static RateLimitRuleEntity toEntity(RateLimitRuleDto dto) {
        RateLimitRuleEntity entity = new RateLimitRuleEntity();
        entity.setId(dto.getId());
        entity.setServiceName(dto.getServiceName());
        entity.setName(dto.getName());
        entity.setHostPatterns(dto.getHostPatterns());
        entity.setPathPatterns(dto.getPathPatterns());
        entity.setHttpMethod(dto.getHttpMethod());
        entity.setCapacity(dto.getCapacity());
        entity.setRefillTokens(dto.getRefillTokens());
        entity.setRefillPeriodSeconds(dto.getRefillPeriodSeconds());
        entity.setDimensions(dto.getDimensions());
        return entity;
    }
}
