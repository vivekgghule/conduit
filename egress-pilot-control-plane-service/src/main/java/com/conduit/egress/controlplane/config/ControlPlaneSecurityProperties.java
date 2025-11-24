package com.conduit.egress.controlplane.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "egress.controlplane.security")
public class ControlPlaneSecurityProperties {

    /**
     * Enables or disables API key enforcement for the control plane.
     */
    private boolean enabled = true;

    /**
     * Expected value for the X-API-KEY header.
     */
    private String apiKey = "changeme-control-plane-key";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
