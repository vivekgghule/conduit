package com.conduit.egress.controlplane.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

class ApiKeyFilter extends OncePerRequestFilter {

    private static final Set<String> OPEN_PATH_PREFIXES = Set.of(
            "/actuator/health",
            "/v3/api-docs",
            "/swagger-ui"
    );

    private final ControlPlaneSecurityProperties properties;

    ApiKeyFilter(ControlPlaneSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return OPEN_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String apiKeyHeader = request.getHeader("X-API-KEY");
        if (apiKeyHeader != null && apiKeyHeader.equals(properties.getApiKey())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("Missing or invalid API key");
    }
}
