package com.consumer.plain.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/call")
public class CallController {

  private final WebClient.Builder builder;
  private final String producerBaseUrl;

  public CallController(WebClient.Builder builder, @Value("${producer.base-url}") String producerBaseUrl) {
    this.builder = builder;
    this.producerBaseUrl = producerBaseUrl;
  }

  @GetMapping("/hello")
  public Mono<String> hello(){
    return builder.baseUrl(producerBaseUrl)
        .build()
        .get()
        .uri("/hello")
        .retrieve()
        .bodyToMono(String.class);
  }

  @GetMapping("/burst")
  public Mono<String> burst(){
    WebClient client = builder.baseUrl(producerBaseUrl).build();
    for(int i=0;i<20;i++){
      client.get().uri("/hello").retrieve().bodyToMono(String.class).subscribe();
    }
    return Mono.just("Sent burst");
  }
}
