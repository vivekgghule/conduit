# Conduit 1.0.0

Control plane + Spring Boot agent starter for centralized outbound rate limiting.

## Modules

- `egress-pilot-core` - contracts + in-memory backend
- `egress-pilot-redis-backend` - Redis / Dragonfly backend (Lua + SHA-256 keys)
- `egress-pilot-agent-autoconfigure` - agent cache, WebClient filter, `@EgressRateLimited` aspect, exhaustion behaviors
- `egress-pilot-agent-starter` - starter dependency exporting the agent auto-config
- `egress-pilot-control-plane-service` - REST rule management backed by PostgreSQL with API key guard and Micrometer metrics
- `egress-pilot-sample-client-service` - reactive demo client hitting GitHub `/rate_limit`
- `infra` - Dockerfiles and Docker Compose stack

## Features

- Rule CRUD at `/api/v1/rules` (requires `X-API-KEY`); PostgreSQL persistence.
- Agents poll, cache, and enforce token buckets on WebClient calls and `@EgressRateLimited` methods.
- Backends: in-memory (per-instance) or Redis/Dragonfly (atomic Lua script with SHA-256 keys).
- Exhaustion behaviors: `BLOCK` (default), `QUEUE` (bounded queue/backoff), `SMOOTH_FLOW` (steady delay).
- Metrics via Actuator + Micrometer (`control_plane.rule.*`, `conduit.egress.agent.*`) and health endpoints.

## Quickstart

1) Build everything: `mvn clean package -DskipTests`  
2) Run stack: `cd infra && docker compose up --build` (Postgres, Redis, control-plane on 8090, sample-client on 8081).  
3) Seed a rule:

```bash
curl -X POST http://localhost:8090/api/v1/rules \
  -H 'X-API-KEY: changeme-control-plane-key' \
  -H 'Content-Type: application/json' \
  -d '{
    "serviceName": "sample-client",
    "name": "github-api",
    "hostPatterns": ["api.github.com"],
    "pathPatterns": ["/rate_limit"],
    "capacity": 60,
    "refillTokens": 60,
    "refillPeriodSeconds": 60
  }'
```

4) Hit the demo: `curl http://localhost:8081/demo/github-rate-limit`

## Configuration

Agent starter (client services):

```yaml
conduit:
  egress:
    agent:
      control-plane-base-url: http://localhost:8090
      control-plane-api-key: ${EGRESS_CONTROLPLANE_API_KEY:changeme-control-plane-key}
      service-name: sample-client
      backend: redis # or in-memory / dragonfly
      redis-uri: redis://localhost:6379
      behavior-on-exhaustion: QUEUE # BLOCK | QUEUE | SMOOTH_FLOW
      queue:
        max-size: 5000
        max-wait-ms: 30000
        backoff-ms: 250
      smooth:
        interval-ms: 50
      fail-open: true
```

Control plane security:

```yaml
egress:
  controlplane:
    security:
      enabled: true
      api-key: ${EGRESS_CONTROLPLANE_API_KEY:changeme-control-plane-key}
```

See `ARCHITECTURE.md` for system flow, `RUNBOOK.md` for ops hooks, `infra/` for deploy assets, and `observability/` for Prometheus/Grafana config.
