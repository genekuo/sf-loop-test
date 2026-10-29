# syntax=docker/dockerfile:1
# Deploy-target image for book-service, built on demand at `sandforge graduate`.
# Multi-stage: build the Spring Boot fat JAR with the Gradle wrapper, then run it on a slim JRE.
# (The repo's normal path is Paketo buildpacks / `bootBuildImage`; this Dockerfile exists only so
# the Sandforge deploy-target stack can `docker compose build` the service with no host toolchain.)

# ---- build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
# Copy the wrapper + build scripts first so dependency resolution can cache across code-only edits.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true
# Now the sources, then build the runnable JAR (tests run in CI, not here).
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test \
 && cp build/libs/*-SNAPSHOT.jar /app.jar

# ---- run stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app
# curl is used by the compose healthcheck (GET /books -> 200).
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
COPY --from=build /app.jar /app/app.jar
EXPOSE 9001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
