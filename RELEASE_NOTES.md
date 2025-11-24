# Conduit 1.0.0 Release Notes

## Highlights
- Control plane for managing outbound rate-limit rules (API key secured, Swagger UI enabled with pre-auth).
- Agent starter with in-memory and Redis/Dragonfly token-bucket backends; supports BLOCK, QUEUE, and SMOOTH_FLOW behaviors.
- Sample apps and Docker Compose stack: producer, plain consumer, Conduit-enabled consumer, PostgreSQL, Redis, control plane, and rule seeder.
- Observability: Micrometer metrics, health endpoints; DEBUG logging ready for agent verification.

## Breaking changes
- Initial OSS release branded as Conduit 1.0.0 (previous internal version 2.0.2).

## How to run
- Build: `mvn clean package -DskipTests`
- Demo stack: `docker compose -f samples/docker-compose.yml up --build`
- Control plane UI: `http://localhost:8090/swagger-ui.html` (API key `changeme-control-plane-key`)

## Known issues
- None tracked for 1.0.0; please report via GitHub issues or `security@conduit.dev` for sensitive findings.
