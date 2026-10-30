# syntax=docker/dockerfile:1

# --- build: compile the Spring Boot jar ------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# Gradle wrapper and build scripts first, so dependencies are cached while only src changes
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

# --- runtime: JRE only, non-root, data in a volume -------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/data && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app
ENV AGENT_DATA_DIR=/app/data
VOLUME /app/data
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fs "http://localhost:${PORT:-8080}/health" || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
