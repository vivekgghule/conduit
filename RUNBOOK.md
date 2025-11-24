# Conduit 1.0.0 Runbook

## Components
- Control Plane: `egress-pilot-control-plane-service` (PostgreSQL-backed rule store + API key filter)
- Agents: client services using `egress-pilot-agent-starter`
- Data stores: PostgreSQL for rule persistence; Redis/Dragonfly for distributed buckets

## Health
- Control plane health: `/actuator/health`
- Agent health: `/actuator/health`
- Backend health indicator: `rateLimitBackendHealthIndicator`

## Metrics
- Control plane: `control_plane.rule.create|update|delete|list`
- Agent counters: `conduit.egress.agent.allowed`, `conduit.egress.agent.denied`, `conduit.egress.agent.queued`, `conduit.egress.agent.queue.dropped`, `conduit.egress.agent.backend.error`, `conduit.egress.agent.backend_error`
- Agent timers: `conduit.egress.agent.invocation{rule,outcome}`, `conduit.egress.agent.rule_refresh{outcome}`
- Agent error counters: `conduit.egress.agent.rule_refresh_error`, `conduit.egress.agent.rule_refresh_backoff`

## Security
- All control-plane APIs except `/actuator/health` and Swagger/OpenAPI require `X-API-KEY`.
- Configure via `EGRESS_CONTROLPLANE_API_KEY` (or disable with `egress.controlplane.security.enabled=false`).

## Emergency Bypass
- Set `conduit.egress.agent.fail-open=true` on a client service to bypass rate limiting when backend or control plane are unhealthy.
