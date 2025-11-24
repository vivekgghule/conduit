package com.conduit.egress.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyTests {

    @Test
    void equalsAndHashCodeShouldMatchForSameValues() {
        RateLimitKey k1 = RateLimitKey.builder("rule")
                .host("api.github.com")
                .path("/rate_limit")
                .method("GET")
                .pkg("com.example")
                .principal("user1")
                .apiKey("k123")
                .build();

        RateLimitKey k2 = RateLimitKey.builder("rule")
                .host("api.github.com")
                .path("/rate_limit")
                .method("GET")
                .pkg("com.example")
                .principal("user1")
                .apiKey("k123")
                .build();

        assertThat(k1).isEqualTo(k2);
        assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
    }

    @Test
    void equalsShouldDifferWhenOneFieldDiffers() {
        RateLimitKey base = RateLimitKey.builder("rule").build();
        RateLimitKey different = RateLimitKey.builder("rule2").build();

        assertThat(base).isNotEqualTo(different);
    }

    @Test
    void toStringContainsAllFields() {
        RateLimitKey key = RateLimitKey.builder("rule")
                .host("host")
                .path("/p")
                .method("POST")
                .pkg("pkg")
                .principal("user")
                .apiKey("ak")
                .build();

        String s = key.toString();
        assertThat(s)
                .contains("name=rule")
                .contains("host=host")
                .contains("path=/p")
                .contains("method=POST")
                .contains("pkg=pkg")
                .contains("principal=user")
                .contains("apiKey=ak");
    }
}
