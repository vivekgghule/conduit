package com.conduit.egress.controlplane.web;

import com.conduit.egress.controlplane.config.ControlPlaneSecurityProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the configured API key for Swagger UI so the "Try it out" flow
 * can prefill the header automatically.
 */
@RestController
@RequestMapping("/swagger-ui")
public class SwaggerUiApiKeyController {

    private final ControlPlaneSecurityProperties securityProperties;

    public SwaggerUiApiKeyController(ControlPlaneSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @GetMapping(value = "/api-key.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> apiKey() {
        return Map.of("apiKey", securityProperties.getApiKey());
    }
}
