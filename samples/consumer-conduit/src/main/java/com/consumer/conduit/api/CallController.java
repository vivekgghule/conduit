package com.consumer.conduit.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@RestController
@RequestMapping("/call")
public class CallController {

  private final WebClient.Builder builder;
  private final String producerBaseUrl;

  public CallController(WebClient.Builder builder,
                        @Value("${producer.base-url}") String producerBaseUrl) {
    this.builder = builder;
    this.producerBaseUrl = producerBaseUrl;
  }

  @GetMapping("/hello")
  public Mono<String> hello(){
    return callProducer("/hello");
  }

  @GetMapping("/burst")
  public Mono<String> burst(){
    WebClient c = builder.baseUrl(producerBaseUrl).build();
    for(int i=0;i<20;i++){
      callProducer(c, "/hello").subscribe();
    }
    return Mono.just("Sent burst with conduit protection");
  }

  private Mono<String> callProducer(String path) {
    return callProducer(builder.baseUrl(producerBaseUrl).build(), path);
  }

  private Mono<String> callProducer(WebClient client, String path) {
    return client.get()
        .uri(path)
        .retrieve()
        .bodyToMono(String.class)
        .retryWhen(
            Retry.backoff(5, java.time.Duration.ofMillis(400))
                .filter(ex -> ex instanceof WebClientResponseException wcre && wcre.getStatusCode().value() == 429)
        );
  }
}
