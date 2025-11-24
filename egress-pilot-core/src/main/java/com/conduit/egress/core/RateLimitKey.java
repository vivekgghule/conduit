package com.conduit.egress.core;

import java.util.Objects;

/**
 * Immutable composite key for identifying a particular rate limit bucket.
 */
public final class RateLimitKey {

    private final String name;
    private final String host;
    private final String path;
    private final String method;
    private final String pkg;
    private final String principal;
    private final String apiKey;

    private RateLimitKey(Builder builder) {
        this.name = builder.name;
        this.host = builder.host;
        this.path = builder.path;
        this.method = builder.method;
        this.pkg = builder.pkg;
        this.principal = builder.principal;
        this.apiKey = builder.apiKey;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public String getPackage() {
        return pkg;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getApiKey() {
        return apiKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RateLimitKey)) return false;
        RateLimitKey that = (RateLimitKey) o;
        return Objects.equals(name, that.name)
                && Objects.equals(host, that.host)
                && Objects.equals(path, that.path)
                && Objects.equals(method, that.method)
                && Objects.equals(pkg, that.pkg)
                && Objects.equals(principal, that.principal)
                && Objects.equals(apiKey, that.apiKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, host, path, method, pkg, principal, apiKey);
    }

    @Override
    public String toString() {
        return "name=" + name +
                ";host=" + host +
                ";path=" + path +
                ";method=" + method +
                ";pkg=" + pkg +
                ";principal=" + principal +
                ";apiKey=" + apiKey;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private String host;
        private String path;
        private String method;
        private String pkg;
        private String principal;
        private String apiKey;

        public Builder(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder pkg(String pkg) {
            this.pkg = pkg;
            return this;
        }

        public Builder principal(String principal) {
            this.principal = principal;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public RateLimitKey build() {
            return new RateLimitKey(this);
        }
    }
}
