# syntax=docker/dockerfile:1
# Single image: backend and playground in one container, for low-cost hosting with one instance.
# nginx listens on PORT, serves the playground and proxies to the backend on 127.0.0.1:BACKEND_PORT.
# Data in /data (AGENT_STORE=memory) or in PostgreSQL (AGENT_STORE=postgres). See DEPLOYMENT.md.

# --- build: the Spring Boot jar --------------------------------------------------------
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /workspace
COPY backend/gradlew backend/settings.gradle backend/build.gradle ./
COPY backend/gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY backend/src src
RUN ./gradlew --no-daemon bootJar -x test \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

# --- build: static files of the playground ---------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ .
RUN npm run build

# --- runtime: JRE + nginx, non-root -----------------------------------------------------
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends nginx gettext-base curl \
    && rm -rf /var/lib/apt/lists/* /etc/nginx/sites-enabled/* \
    && useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app /data && chown -R app:app /app /data
WORKDIR /app
COPY --from=backend --chown=app:app /workspace/app.jar app.jar
COPY --from=frontend /app/dist /usr/share/nginx/html
COPY docker/single/nginx.conf.template /etc/agent-server/nginx.conf.template
COPY --chmod=755 docker/single/entrypoint.sh /usr/local/bin/entrypoint.sh
USER app
ENV PORT=8080 \
    BACKEND_PORT=8081 \
    AGENT_DATA_DIR=/data \
    JAVA_OPTS="-XX:MaxRAMPercentage=60"
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fs "http://127.0.0.1:${PORT}/health" || exit 1
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
