package com.conduit.egress.sample;

import com.conduit.egress.agent.EgressRateLimited;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ExternalApiClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiClient.class);

    private final WebClient webClient;

    public ExternalApiClient(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://api.github.com")
                .build();
    }

    @EgressRateLimited("github-api")
    @Retry(name = "github-api")
    @CircuitBreaker(name = "github-api")
    public Mono<String> fetchRateLimit() {
        return webClient.get()
                .uri("/rate_limit")
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> log.info("Received response length={}", body.length()));
    }
}
