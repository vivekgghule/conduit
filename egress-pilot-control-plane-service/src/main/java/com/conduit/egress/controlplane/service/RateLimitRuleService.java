package com.conduit.egress.controlplane.service;

import com.conduit.egress.controlplane.model.RateLimitRuleEntity;
import com.conduit.egress.controlplane.repo.RateLimitRuleRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class RateLimitRuleService {

    private final RateLimitRuleRepository repository;

    public RateLimitRuleService(RateLimitRuleRepository repository) {
        this.repository = repository;
    }

    @CircuitBreaker(name = "rule-store", fallbackMethod = "fallbackPageByService")
    public Page<RateLimitRuleEntity> findByServiceName(String serviceName, Pageable pageable) {
        return repository.findByServiceName(serviceName, pageable);
    }

    @CircuitBreaker(name = "rule-store", fallbackMethod = "fallbackPage")
    public Page<RateLimitRuleEntity> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @CircuitBreaker(name = "rule-store", fallbackMethod = "fallbackOptionalById")
    public Optional<RateLimitRuleEntity> findById(Long id) {
        return repository.findById(id);
    }

    @CircuitBreaker(name = "rule-store", fallbackMethod = "fallbackOptionalByServiceAndName")
    public Optional<RateLimitRuleEntity> findByServiceNameAndName(String serviceName, String name) {
        return repository.findByServiceNameAndName(serviceName, name);
    }

    @CircuitBreaker(name = "rule-store", fallbackMethod = "fallbackExistsByServiceAndName")
    public boolean existsByServiceNameAndName(String serviceName, String name) {
        return repository.existsByServiceNameAndName(serviceName, name);
    }

    @CircuitBreaker(name = "rule-store", fallbackMethod = "fallbackExistsById")
    public boolean existsById(Long id) {
        return repository.existsById(id);
    }

    @CircuitBreaker(name = "rule-store", fallbackMethod = "fallbackSave")
    public RateLimitRuleEntity save(RateLimitRuleEntity entity) {
        return repository.save(entity);
    }

    @CircuitBreaker(name = "rule-store", fallbackMethod = "fallbackDeleteById")
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    private Page<RateLimitRuleEntity> fallbackPageByService(String serviceName, Pageable pageable, Throwable ex) {
        throw unavailable(ex);
    }

    private Page<RateLimitRuleEntity> fallbackPage(Pageable pageable, Throwable ex) {
        throw unavailable(ex);
    }

    private Optional<RateLimitRuleEntity> fallbackOptionalById(Long id, Throwable ex) {
        throw unavailable(ex);
    }

    private Optional<RateLimitRuleEntity> fallbackOptionalByServiceAndName(String serviceName, String name, Throwable ex) {
        throw unavailable(ex);
    }

    private boolean fallbackExistsByServiceAndName(String serviceName, String name, Throwable ex) {
        throw unavailable(ex);
    }

    private boolean fallbackExistsById(Long id, Throwable ex) {
        throw unavailable(ex);
    }

    private RateLimitRuleEntity fallbackSave(RateLimitRuleEntity entity, Throwable ex) {
        throw unavailable(ex);
    }

    private void fallbackDeleteById(Long id, Throwable ex) {
        throw unavailable(ex);
    }

    private ResponseStatusException unavailable(Throwable ex) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Rule store unavailable", ex);
    }
}
