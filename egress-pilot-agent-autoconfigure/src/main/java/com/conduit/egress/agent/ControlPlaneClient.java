package com.conduit.egress.agent;

import com.conduit.egress.agent.dto.ControlPlaneRuleDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * Reactive client used by agents to fetch rules from control-plane.
 */
public class ControlPlaneClient {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneClient.class);

    private final WebClient webClient;
    private final EgressAgentProperties properties;

    public ControlPlaneClient(EgressAgentProperties properties) {
        this.webClient = WebClient.builder()
                .baseUrl(properties.getControlPlaneBaseUrl())
                .build();
        this.properties = properties;
    }

    @CircuitBreaker(name = "control-plane", fallbackMethod = "fallbackRules")
    public Flux<ControlPlaneRuleDTO> fetchRules() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/rules")
                        .queryParam("service", properties.getServiceName())
                        .build())
                .header("X-API-KEY", properties.getControlPlaneApiKey())
                .retrieve()
                .bodyToFlux(ControlPlaneRuleDTO.class)
                .doOnSubscribe(sub -> log.info("Fetching egress rules from control-plane for service={}",
                        properties.getServiceName()));
    }

    @SuppressWarnings("unused")
    public Flux<ControlPlaneRuleDTO> fallbackRules(Throwable t) {
        log.warn("Control-plane unavailable, using last known rules", t);
        return Flux.empty();
    }
}
