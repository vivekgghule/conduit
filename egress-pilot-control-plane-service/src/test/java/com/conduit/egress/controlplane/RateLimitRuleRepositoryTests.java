package com.conduit.egress.controlplane;

import com.conduit.egress.controlplane.model.RateLimitRuleEntity;
import com.conduit.egress.controlplane.repo.RateLimitRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RateLimitRuleRepositoryTests {

    @Autowired
    private RateLimitRuleRepository repository;

    @Test
    void findsByServiceName() {
        RateLimitRuleEntity e = new RateLimitRuleEntity();
        e.setServiceName("sample-client");
        e.setName("github-api");
        e.setHostPatterns(List.of("api.github.com"));
        e.setPathPatterns(List.of("/rate_limit", "/repos/*"));
        e.setCapacity(60);
        e.setRefillTokens(60);
        e.setRefillPeriodSeconds(60);

        repository.save(e);

        List<RateLimitRuleEntity> found = repository.findByServiceName("sample-client");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).isEqualTo("github-api");
        assertThat(found.get(0).getHostPatterns()).containsExactly("api.github.com");
        assertThat(found.get(0).getPathPatterns()).contains("/rate_limit", "/repos/*");
    }
}
