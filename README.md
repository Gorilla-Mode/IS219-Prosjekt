# IS-219

Kotlin/Spring Boot prototype with registration, login, PostgreSQL,
and Spring AI configured for optional Ollama use.

Requires Docker and Docker Compose 2.32.2+.

Start: `docker compose up`

Develop: `docker compose up --watch`

Open: http://localhost:8080

Stop: `docker compose down`

Reset database (deletes data):
`docker compose down && docker volume rm is219-ugc-pgdata && docker compose up`

Passwords are stored in plaintext for this local prototype.
AI is disabled by default.
