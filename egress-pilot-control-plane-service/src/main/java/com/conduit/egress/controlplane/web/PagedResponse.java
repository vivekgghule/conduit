package com.conduit.egress.controlplane.web;

import java.util.List;

/**
 * Simple wrapper for paginated responses so callers receive both data and metadata.
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<String> sort
) {
}
