# syntax=docker/dockerfile:1
FROM eclipse-temurin:21.0.12.1_1-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src ./src
RUN --mount=type=cache,target=/gradle-cache GRADLE_USER_HOME=/gradle-cache ./gradlew bootJar && cp -a /gradle-cache /root/.gradle

FROM build AS development
ENV SPRING_PROFILES_ACTIVE=dev
CMD ["./gradlew", "bootRun"]

FROM docker:29.7.2-cli AS docker-cli

FROM build AS tests
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker-cli /usr/local/libexec/docker/cli-plugins/docker-compose /usr/local/libexec/docker/cli-plugins/docker-compose
COPY --from=docker-cli /usr/local/libexec/docker/cli-plugins/docker-buildx /usr/local/libexec/docker/cli-plugins/docker-buildx
RUN apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/*
COPY . .
CMD ["sh", "-c", "./gradlew test && python3 tests/container_lifecycle.py"]

FROM eclipse-temurin:21.0.12.1_1-jre-jammy AS runtime
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar ./app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
