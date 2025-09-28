###############################################################################
# Dockerfile for SmartChat Backend (Spring Boot API)
# ---------------------------------------------------------------------------
# - Multi-stage build for smaller runtime image.
# - Configurable ports via .env (API_PORT & DEBUG_PORT).
# - Debugging support (ENABLE_DEBUG=true).
# - Log directory mapped to host (backend/logs/api).
###############################################################################

# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy Maven configuration and download dependencies first (cache efficient)
COPY pom.xml ./
RUN mvn -q -e -DskipTests dependency:go-offline

# Copy actual source code
COPY src ./src

# Build JAR file
RUN mvn -q -DskipTests package

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# ===== Default Environment Variables =====
# Can be overridden by .env or docker-compose.yml
ENV TZ=${TZ:-Asia/Kolkata}
ENV ENABLE_DEBUG=false
ENV DEBUG_SUSPEND=n
ENV DEBUG_PORT=5005
ENV API_PORT=8080

# Copy built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Create log directory inside container and allow writing
RUN mkdir -p /var/log/api && chmod -R 777 /var/log/api

# Expose ports dynamically:
# - API_PORT → REST API
# - DEBUG_PORT → JVM remote debugging
EXPOSE ${API_PORT} ${DEBUG_PORT}

# Entrypoint:
# - If ENABLE_DEBUG=true → starts JVM with debug agent
# - Else → starts normally
ENTRYPOINT ["/bin/sh", "-c", "\
  if [ \"$ENABLE_DEBUG\" = \"true\" ]; then \
    echo '🚀 Starting in DEBUG mode on port '$DEBUG_PORT' (suspend='$DEBUG_SUSPEND')...'; \
    exec java -agentlib:jdwp=transport=dt_socket,server=y,suspend=$DEBUG_SUSPEND,address=*:$DEBUG_PORT -jar /app/app.jar --server.port=$API_PORT; \
  else \
    echo '🚀 Starting in NORMAL mode on port '$API_PORT'...'; \
    exec java -jar /app/app.jar --server.port=$API_PORT; \
  fi \
"]
