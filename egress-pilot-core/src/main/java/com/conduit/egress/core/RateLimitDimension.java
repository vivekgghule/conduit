package com.conduit.egress.core;

/**
 * Dimensions that can form a composite rate limit key.
 */
public enum RateLimitDimension {
    HOST,
    PATH,
    METHOD,
    PACKAGE,
    PRINCIPAL,
    API_KEY
}
