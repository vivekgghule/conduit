package com.conduit.egress.sample;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class ExternalApiController {

    private final ExternalApiClient client;

    public ExternalApiController(ExternalApiClient client) {
        this.client = client;
    }

    @GetMapping("/demo/github-rate-limit")
    public Mono<String> demo() {
        return client.fetchRateLimit();
    }
}
