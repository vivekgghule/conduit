package com.conduit.egress.controlplane.repo;

import com.conduit.egress.controlplane.model.RateLimitRuleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RateLimitRuleRepository extends JpaRepository<RateLimitRuleEntity, Long> {

    List<RateLimitRuleEntity> findByServiceName(String serviceName);

    Page<RateLimitRuleEntity> findByServiceName(String serviceName, Pageable pageable);

    Optional<RateLimitRuleEntity> findByServiceNameAndName(String serviceName, String name);

    boolean existsByServiceNameAndName(String serviceName, String name);
}
