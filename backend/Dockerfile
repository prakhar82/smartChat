###############################################################################
# 🧠 SmartChat Backend - Dockerfile (Spring Boot API)
# -----------------------------------------------------------------------------
# Features:
# ✅ Multi-stage build for minimal runtime size
# ✅ Maven dependency caching
# ✅ ENV-based dynamic port control
# ✅ JVM remote debugging support
# ✅ Logs written to /var/log/api (mapped to host)
# ✅ Compatible with docker-compose.yml & .env
#
# Author: SmartChat Contributors
# Date: 2025
###############################################################################

# =========================
# 🔨 Build Stage
# =========================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy Maven project files and prefetch dependencies (for faster rebuilds)
COPY pom.xml ./
RUN mvn -B -q -e -DskipTests dependency:go-offline

# Copy source and build the JAR
COPY src ./src
RUN mvn -B -q -DskipTests package

# =========================
# 🚀 Runtime Stage
# =========================
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# ----------- Environment Variables -----------
ENV TZ=${TZ:-Asia/Kolkata}
ENV ENABLE_DEBUG=false
ENV DEBUG_SUSPEND=n
ENV DEBUG_PORT=5005
ENV API_PORT=8080

# ----------- System Setup -----------
RUN apk add --no-cache curl bash tzdata \
    && mkdir -p /var/log/api \
    && chmod -R 777 /var/log/api \
    && echo "$TZ" > /etc/timezone

# ----------- Copy JAR from Build Stage -----------
COPY --from=build /app/target/*.jar /app/app.jar

# ----------- Ports -----------
EXPOSE ${API_PORT}
EXPOSE ${DEBUG_PORT}

# ----------- Healthcheck (Spring Boot Actuator) -----------
# Uses internal port API_PORT to test readiness
HEALTHCHECK --interval=30s --timeout=5s --start-period=25s --retries=3 \
  CMD curl -fsS http://localhost:${API_PORT}/actuator/health || exit 1

# ----------- Startup Script -----------
ENTRYPOINT ["/bin/sh", "-c", "\
  if [ \"$ENABLE_DEBUG\" = \"true\" ]; then \
    echo '🛠️  Starting SmartChat API in DEBUG mode (port '$DEBUG_PORT', suspend='$DEBUG_SUSPEND')...'; \
    exec java -agentlib:jdwp=transport=dt_socket,server=y,suspend=$DEBUG_SUSPEND,address=*:$DEBUG_PORT \
         -jar /app/app.jar --server.port=$API_PORT; \
  else \
    echo '🚀 Starting SmartChat API in NORMAL mode on port '$API_PORT'...'; \
    exec java -jar /app/app.jar --server.port=$API_PORT; \
  fi \
"]
