package com.conduit.egress.sample;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ExternalApiClientTests {

    @Test
    void fetchRateLimitUsesWebClient() {
        WebClient.Builder builder = Mockito.mock(WebClient.Builder.class);
        WebClient webClient = Mockito.mock(WebClient.class);
        WebClient.RequestHeadersUriSpec<?> req = Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec<?> spec = Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec resp = Mockito.mock(WebClient.ResponseSpec.class);

        Mockito.when(builder.baseUrl("https://api.github.com")).thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(webClient);
        Mockito.<WebClient.RequestHeadersUriSpec<?>>when(webClient.get()).thenReturn(req);
        Mockito.<WebClient.RequestHeadersSpec<?>>when(req.uri("/rate_limit")).thenReturn(spec);
        Mockito.when(spec.retrieve()).thenReturn(resp);
        String payload = "{\"rate\":\"ok\"}";
        Mockito.when(resp.bodyToMono(String.class)).thenReturn(Mono.just(payload));

        ExternalApiClient client = new ExternalApiClient(builder);

        StepVerifier.create(client.fetchRateLimit())
                .expectNext(payload)
                .verifyComplete();
    }
}
