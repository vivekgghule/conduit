# Contributing to Conduit

Thank you for your interest in contributing!

## How to contribute
- **Issues:** Use GitHub issues for bugs and feature requests. Include steps to reproduce and expected/actual behavior.
- **Pull requests:** Fork, branch from `main`, and submit a PR. Keep changes focused; include tests where applicable.
- **Coding standards:** Follow existing style and formatting; keep comments concise and purposeful.
- **Commits:** Use clear messages. Squash/fixup before merge as needed.

## Development quickstart
- JDK 21, Maven 3.9+
- Build: `mvn clean package -DskipTests`
- Run demo stack: `docker compose -f samples/docker-compose.yml up --build`

## Code of conduct
Please be respectful and constructive. Harassment or abusive behavior is not tolerated.

## Security
Please **do not** file public issues for vulnerabilities. See `SECURITY.md` for private disclosure.
