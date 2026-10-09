# Project instructions

- Run builds, tests, and the application locally with the Gradle wrapper and JDK 27.
- Use Kotlin, Spring MVC, Thymeleaf, and a thread-safe in-memory account repository.
- Keep controllers, services, repositories, and form models separate.
- Keep account insertion atomic and usernames normalized; seed test/test at startup.
- Accounts are temporary and reset when the application restarts. Do not add database dependencies.
- Preserve CSRF protection; never log passwords.
- Plaintext passwords are intentional for this local prototype.
- Keep AI optional and documentation brief.
