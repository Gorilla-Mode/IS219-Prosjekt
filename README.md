# IS-219

A simple Kotlin, Spring Boot, and Thymeleaf project with registration and login.
Accounts are stored in memory and reset when the application restarts.
AI is optional and disabled by default.

Uses Kotlin 2.4.21, Spring Boot 4.1.1, and Gradle 9.8.1.
Requires JDK 27. Set `JAVA_HOME` to your JDK 27 installation.
Java and Kotlin target Java 26 bytecode, the highest target supported by Kotlin 2.4.21.

Run these commands from the repository root:

Build: `./gradlew build`

Run: `./gradlew bootRun --args='--spring.profiles.active=dev'`

Test: `./gradlew test`

Open http://localhost:8080 (port **8080**). Stop with Ctrl+C.

Default account, created at every startup:

| Username | Password |
|----------|----------|
| test     | test     |
