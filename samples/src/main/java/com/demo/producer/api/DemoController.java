package com.demo.producer.api;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DemoController {

  private final RateLimiterService rateLimiter;

  public DemoController(RateLimiterService rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  @GetMapping("/hello")
  public ResponseEntity<String> hello() { return respond("Hello!"); }

  @GetMapping("/hello/{id}")
  public ResponseEntity<String> helloId(@PathVariable String id) { return respond("Hello " + id); }

  @PostMapping("/create")
  public ResponseEntity<String> create(@RequestBody String payload){ return respond("Created: " + payload); }

  @GetMapping("/path/{user}/details")
  public ResponseEntity<String> userDetails(@PathVariable String user){ return respond("User = " + user); }

  @GetMapping("/secured")
  public ResponseEntity<String> secured(@RequestHeader(value="X-API-KEY", required=false) String key){
    return respond("API KEY = " + key);
  }

  private ResponseEntity<String> respond(String body) {
    if (!rateLimiter.allow()) {
      return ResponseEntity.status(429).body("Too Many Requests (producer throttle)");
    }
    return ResponseEntity.ok(body);
  }
}
