# Samples stack

This stack runs:
- Producer sample (`producer`): simple API on `8081` with `/api/hello`.
- Consumer without Conduit (`consumer-plain`): calls producer without rate limiting on `8082`.
- Consumer with Conduit (`consumer-conduit`): uses the Conduit agent to rate-limit outbound calls on `8083`.
- Control plane + Postgres + Redis, plus a seeder that installs a rule for `consumer-conduit`.

## Run
```bash
docker compose -f samples/docker-compose.yml up --build
```

After startup:
- Control plane: http://localhost:8090/swagger-ui.html (API key `changeme-control-plane-key`)
- Producer: http://localhost:8081/api/hello
- Consumer without conduit: http://localhost:8082/call/hello
- Consumer with conduit: http://localhost:8083/call/hello

The seeder creates a rule for service `consumer-conduit` targeting host `producer` and the producer API paths.
