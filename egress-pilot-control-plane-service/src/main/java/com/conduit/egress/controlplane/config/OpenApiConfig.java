package com.conduit.egress.controlplane.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI controlPlaneOpenApi(ControlPlaneSecurityProperties securityProperties) {
        SecurityScheme apiKeyScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-API-KEY")
                .description("Default: " + securityProperties.getApiKey());
        return new OpenAPI()
                .info(new Info()
                        .title("Egress Control Plane API")
                        .description("REST endpoints to manage outbound rate limit rules")
                        .version("v1"))
                .components(new Components().addSecuritySchemes("apiKey", apiKeyScheme))
                .addSecurityItem(new SecurityRequirement().addList("apiKey"));
    }
}
