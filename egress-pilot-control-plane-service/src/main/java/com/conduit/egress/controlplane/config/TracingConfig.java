package com.conduit.egress.controlplane.config;

import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

/**
 * Ensures trace/span ids are available in logs via MDC.
 */
@Configuration
public class TracingConfig {

    @Bean
    public OncePerRequestFilter traceLoggingFilter(Tracer tracer) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                try {
                    if (tracer.currentSpan() != null) {
                        MDC.put("traceId", tracer.currentSpan().context().traceId());
                        MDC.put("spanId", tracer.currentSpan().context().spanId());
                    }
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.remove("traceId");
                    MDC.remove("spanId");
                }
            }
        };
    }
}
