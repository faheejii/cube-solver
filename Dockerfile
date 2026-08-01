FROM node:22-alpine AS frontend-build

WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-25 AS backend-build

WORKDIR /build
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src/ ./src/
RUN mvn -q -DskipTests package dependency:copy-dependencies -DoutputDirectory=target/dependency

FROM eclipse-temurin:25-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 cube-solver

WORKDIR /app
COPY --from=backend-build /build/target/classes ./classes
COPY --from=backend-build /build/target/dependency ./dependency
COPY --from=frontend-build /build/frontend/dist ./frontend/dist

USER cube-solver
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl --fail --silent http://localhost:${SERVER_PORT:-8080}/api/health/ready || exit 1

ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${SERVER_PORT:-8080} -Dfrontend.dist=/app/frontend/dist ${JAVA_OPTS:-} -cp '/app/classes:/app/dependency/*' server.ApiServerMain"]
