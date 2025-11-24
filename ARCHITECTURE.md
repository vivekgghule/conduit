# Conduit 1.0.0 Architecture

## Overview

Conduit 1.0.0 uses a control-plane + agent model:

- Central control plane stores and exposes rate limit rules via REST API backed by PostgreSQL and guarded by an API key filter.
- Agents (Spring Boot starter) poll the control-plane, cache rules locally, and enforce token-bucket limits for outbound calls.

## Flow

1. Operator configures rules in control-plane via `/api/v1/rules` (secured with `X-API-KEY`).
2. Agents poll `/api/v1/rules?service={serviceName}` using `ControlPlaneClient` with Resilience4j circuit breaker.
3. `RuleCache` transforms DTOs into `RateLimitConfig` objects and keeps an in-memory list.
4. For each outbound WebClient call:
   - `WebClientRateLimiterFilter` finds a matching rule via host/path/method.
   - Builds `RateLimitKey` from configured dimensions.
   - Uses pluggable backend (`InMemoryTokenBucketBackend` or `RedisTokenBucketBackend`) to decide allow/deny.
   - Applies configured exhaustion behavior: `BLOCK`, `QUEUE` (bounded waiting), or `SMOOTH_FLOW` (steady delay).
5. Methods annotated with `@EgressRateLimited("rule-name")` are intercepted by the same backend.

## Backends

- In-memory: lightweight, per-instance token buckets.
- Redis / Dragonfly: atomic token buckets with Lua script and SHA-256 keys for safe, distributed limits.
