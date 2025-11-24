package com.conduit.egress.controlplane;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.conduit.egress.controlplane.repo.RateLimitRuleRepository;
import org.junit.jupiter.api.BeforeEach;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitRuleControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitRuleRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void requestsWithoutApiKeyAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/rules?service=sample-client"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRulesInitiallyEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/rules?service=sample-client")
                        .header("X-API-KEY", "changeme-control-plane-key"))
                .andExpect(status().isOk());
    }

    @Test
    void createRule() throws Exception {
        String json = """
                {
                  "serviceName": "sample-client",
                  "name": "github-api",
                  "hostPatterns": ["api.github.com"],
                  "pathPatterns": ["/rate_limit"],
                  "capacity": 60,
                  "refillTokens": 60,
                  "refillPeriodSeconds": 60
                }
                """;
        mockMvc.perform(post("/api/v1/rules")
                        .header("X-API-KEY", "changeme-control-plane-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void duplicateRuleReturnsConflict() throws Exception {
        String json = """
                {
                  "serviceName": "sample-client",
                  "name": "github-api",
                  "hostPatterns": ["api.github.com"],
                  "pathPatterns": ["/rate_limit"],
                  "capacity": 60,
                  "refillTokens": 60,
                  "refillPeriodSeconds": 60
                }
                """;
        mockMvc.perform(post("/api/v1/rules")
                        .header("X-API-KEY", "changeme-control-plane-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/rules")
                        .header("X-API-KEY", "changeme-control-plane-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict());
    }
}
