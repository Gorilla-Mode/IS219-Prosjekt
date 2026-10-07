# IS-219

Kotlin/Spring Boot prototype with username/password registration, login, in-memory accounts,
and Spring AI configured for optional Ollama use.

Requires JDK 21. Set `JAVA_HOME` to your JDK 21 installation.

Start from the repository root: `./gradlew bootRun --args='--spring.profiles.active=dev'`

Test: `./gradlew test`

Build: `./gradlew build`

Open: http://localhost:8080

Stop with Ctrl+C. Restart after Kotlin or configuration changes.
With the `dev` profile, template edits appear on browser refresh.

Every startup creates username `test` with password `test`.
Registered accounts live only in memory and are lost on restart.
The old PostgreSQL volume is preserved; its accounts are not migrated.

Passwords are stored in plaintext for this local prototype.
AI is disabled by default.
Enable it with the `ai` profile alongside `dev`; Ollama defaults to
`http://localhost:11434` (`OLLAMA_URL` and `OLLAMA_MODEL` can override it).
