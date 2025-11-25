package com.conduit.egress.controlplane;

import com.conduit.egress.controlplane.model.RateLimitRuleEntity;
import com.conduit.egress.controlplane.repo.RateLimitRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
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
    void listRulesEnforcesValidation() throws Exception {
        mockMvc.perform(get("/api/v1/rules")
                        .header("X-API-KEY", "changeme-control-plane-key")
                        .param("service", " ")
                        .param("page", "-1")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void rejectsSqlInjectionInServiceParam() throws Exception {
        mockMvc.perform(get("/api/v1/rules")
                        .header("X-API-KEY", "changeme-control-plane-key")
                        .param("service", "sample-client' OR '1'='1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void createRuleRejectsSqlInjectionInIdentifiers() throws Exception {
        String json = """
                {
                  "serviceName": "sample-client'; drop table users; --",
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
                .andExpect(status().isBadRequest());
    }

    @Test
    void listRulesSupportsPaginationAndSorting() throws Exception {
        repository.save(buildRule("sample-client", "b-rule"));
        repository.save(buildRule("sample-client", "a-rule"));
        repository.save(buildRule("sample-client", "c-rule"));

        mockMvc.perform(get("/api/v1/rules")
                        .header("X-API-KEY", "changeme-control-plane-key")
                        .param("service", "sample-client")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("a-rule"))
                .andExpect(jsonPath("$.items[1].name").value("b-rule"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void invalidSortPropertyReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rules")
                        .header("X-API-KEY", "changeme-control-plane-key")
                        .param("service", "sample-client")
                        .param("sort", "nonexistent,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unsupported sort property")));
    }

    @Test
    void updateRuleNotFoundReturns404() throws Exception {
        String json = """
                {
                  "serviceName": "sample-client",
                  "name": "missing",
                  "hostPatterns": ["example.com"],
                  "pathPatterns": ["/"],
                  "capacity": 10,
                  "refillTokens": 1,
                  "refillPeriodSeconds": 1
                }
                """;
        mockMvc.perform(put("/api/v1/rules/{id}", 123)
                        .header("X-API-KEY", "changeme-control-plane-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRuleConflictReturns409() throws Exception {
        RateLimitRuleEntity existing = repository.save(buildRule("sample-client", "first"));
        RateLimitRuleEntity second = repository.save(buildRule("sample-client", "second"));

        String updateJson = """
                {
                  "serviceName": "sample-client",
                  "name": "first",
                  "hostPatterns": ["example.com"],
                  "pathPatterns": ["/"],
                  "capacity": 10,
                  "refillTokens": 1,
                  "refillPeriodSeconds": 1
                }
                """;

        mockMvc.perform(put("/api/v1/rules/{id}", second.getId())
                        .header("X-API-KEY", "changeme-control-plane-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteRuleReturnsProperCodes() throws Exception {
        RateLimitRuleEntity saved = repository.save(buildRule("sample-client", "to-delete"));

        mockMvc.perform(delete("/api/v1/rules/{id}", saved.getId())
                        .header("X-API-KEY", "changeme-control-plane-key"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/rules/{id}", saved.getId())
                        .header("X-API-KEY", "changeme-control-plane-key"))
                .andExpect(status().isNotFound());
    }

    @Test
    void bulkImportCreatesAndReportsErrors() throws Exception {
        repository.save(buildRule("sample-client", "existing"));

        String payload = """
                [
                  {
                    "serviceName": "sample-client",
                    "name": "new-rule",
                    "hostPatterns": ["example.com"],
                    "pathPatterns": ["/"],
                    "capacity": 10,
                    "refillTokens": 1,
                    "refillPeriodSeconds": 1
                  },
                  {
                    "serviceName": "sample-client",
                    "name": "existing",
                    "hostPatterns": ["example.com"],
                    "pathPatterns": ["/"],
                    "capacity": 10,
                    "refillTokens": 1,
                    "refillPeriodSeconds": 1
                  }
                ]
                """;
        MockMultipartFile file = new MockMultipartFile("file", "rules.json", "application/json", payload.getBytes());

        mockMvc.perform(multipart("/api/v1/rules/bulk")
                        .file(file)
                        .header("X-API-KEY", "changeme-control-plane-key"))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.created.length()").value(1))
                .andExpect(jsonPath("$.errors.length()").value(1));
    }

    @Test
    void bulkImportRejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "rules.json", "application/json", new byte[0]);
        mockMvc.perform(multipart("/api/v1/rules/bulk")
                        .file(file)
                        .header("X-API-KEY", "changeme-control-plane-key"))
                .andExpect(status().isBadRequest());
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

    private RateLimitRuleEntity buildRule(String service, String name) {
        RateLimitRuleEntity e = new RateLimitRuleEntity();
        e.setServiceName(service);
        e.setName(name);
        e.setHostPatterns(java.util.List.of("example.com"));
        e.setPathPatterns(java.util.List.of("/"));
        e.setCapacity(10);
        e.setRefillTokens(1);
        e.setRefillPeriodSeconds(1);
        return e;
    }
}
