###############################################################################
# 🏗️ SMARTCHAT UNIVERSAL BACKEND BUILDER — Optimized for Production (Stable)
###############################################################################

# syntax=docker/dockerfile:1.6

FROM maven:3.9.8-eclipse-temurin-17 AS build

LABEL maintainer="SmartChat DevOps Team <team@smartchat.app>" \
      org.opencontainers.image.title="SmartChat Backend Module" \
      org.opencontainers.image.source="https://github.com/smartchat/backend" \
      org.opencontainers.image.licenses="Proprietary"

WORKDIR /build

# ---------------------------------------------------------------------------
# ⚙️ COPY PROJECT FILES (Leverage Docker cache with BuildKit)
# ---------------------------------------------------------------------------
COPY backend/pom.xml ./backend/pom.xml
RUN mkdir -p /root/.m2
COPY backend ./backend

# ---------------------------------------------------------------------------
# 🔨 BUILD SELECTED MODULE (and smartchat-common) with Caching
# ---------------------------------------------------------------------------
ARG MODULE_NAME
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    --mount=type=cache,target=/root/.cache/mvn,sharing=locked \
    echo "🔨 Building SmartChat module: ${MODULE_NAME}" && \
    cd backend && \
    # ❌ Removed -T 1C to prevent Maven repo lock contention
    MAVEN_OPTS='-Xmx1g -Duser.home=/root' mvn -B clean package -DskipTests \
        -pl ${MODULE_NAME},smartchat-common -am && \
    echo "✅ Build completed for ${MODULE_NAME}"

# ---------------------------------------------------------------------------
# 🔐 SECURITY VALIDATION (ReactiveSecurity Classes)
# ---------------------------------------------------------------------------
RUN echo "🔍 Verifying security artifacts for ${MODULE_NAME}..." && \
    if [ "${MODULE_NAME}" != "smartchat-discovery" ] && [ "${MODULE_NAME}" != "smartchat-builder" ]; then \
        if jar tf backend/${MODULE_NAME}/target/*.jar | grep -q "smartchat-common"; then \
            mkdir -p backend/${MODULE_NAME}/target/BOOT-INF/lib; \
            ( jar tf backend/${MODULE_NAME}/target/*.jar | grep -q "ReactiveSecurityConfigBase.class" || \
              (jar xf backend/${MODULE_NAME}/target/*.jar BOOT-INF/lib || true && \
               find backend/${MODULE_NAME}/target/BOOT-INF/lib -type f -name "*.jar" -exec jar tf {} \; | \
               grep -q "ReactiveSecurityConfigBase.class" || \
               (echo "❌ Missing ReactiveSecurityConfigBase.class" && exit 1)) ); \
            ( jar tf backend/${MODULE_NAME}/target/*.jar | grep -q "JwtReactiveAuthenticationFilter.class" || \
              (find backend/${MODULE_NAME}/target/BOOT-INF/lib -type f -name "*.jar" -exec jar tf {} \; | \
               grep -q "JwtReactiveAuthenticationFilter.class" || \
               (echo "❌ Missing JwtReactiveAuthenticationFilter.class" && exit 1)) ); \
            echo "✅ Security verification passed for ${MODULE_NAME}"; \
        else \
            echo "ℹ️ ${MODULE_NAME} does not bundle smartchat-common — skipping security class check."; \
        fi; \
    else \
        echo "ℹ️ Skipping verification for ${MODULE_NAME} (non-secured module)."; \
    fi

# ---------------------------------------------------------------------------
# ✅ Verify SecurityConfig contains addFilterAfter (AUTHORIZATION chain)
# ---------------------------------------------------------------------------
RUN echo "[SECURITY] Verifying SecurityConfig filter placement..." && \
    JAR_PATH=$(find backend/${MODULE_NAME}/target -maxdepth 1 -type f -name "${MODULE_NAME}*.jar" | head -n 1) && \
    if [ -n "$JAR_PATH" ] && [ -f "$JAR_PATH" ]; then \
        echo "[SECURITY] Found JAR: $JAR_PATH"; \
        mkdir -p backend/${MODULE_NAME}/target/tmp_check && cd backend/${MODULE_NAME}/target/tmp_check; \
        if jar tf "$JAR_PATH" | grep -q "BOOT-INF/classes/com/smartchat/api/config/SecurityConfig.class"; then \
            echo "[SECURITY] Detected Boot JAR layout"; \
            jar xf "$JAR_PATH" BOOT-INF/classes/com/smartchat/api/config/SecurityConfig.class; \
            CLASS_FILE="BOOT-INF/classes/com/smartchat/api/config/SecurityConfig.class"; \
        elif jar tf "$JAR_PATH" | grep -q "com/smartchat/api/config/SecurityConfig.class"; then \
            echo "[SECURITY] Detected plain JAR layout"; \
            jar xf "$JAR_PATH" com/smartchat/api/config/SecurityConfig.class; \
            CLASS_FILE="com/smartchat/api/config/SecurityConfig.class"; \
        else \
            echo "[SECURITY] WARNING: SecurityConfig.class not found in $JAR_PATH (non-API module, skipping check)"; \
            cd /build && exit 0; \
        fi; \
        if grep -aq "addFilterAfter" "$CLASS_FILE"; then \
            echo "[SECURITY] PASS: Using addFilterAfter (AUTHORIZATION chain)"; \
        else \
            echo "[SECURITY] FAIL: Detected old SecurityConfig (addFilterAt) - rebuild required!" && exit 1; \
        fi; \
        cd /build; \
    else \
        echo "[SECURITY] WARNING: No JAR found for ${MODULE_NAME}, skipping SecurityConfig check."; \
    fi


###############################################################################
# 🚀 STAGE 2 — RUNTIME IMAGE (Lightweight JRE)
###############################################################################
FROM eclipse-temurin:17-jre-alpine AS runtime

ARG MODULE_NAME
ARG MODULE_PORT=8080
USER root

RUN apk add --no-cache curl grep tzdata && \
    ln -snf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone && \
    apk add --no-cache openjdk17 --repository=https://dl-cdn.alpinelinux.org/alpine/edge/community && \
    ln -sf /usr/lib/jvm/java-17-openjdk/bin/jar /usr/bin/jar && \
    echo "🧰 Added 'jar' command for inspection (safe for dev builds)"

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 \
 -Dspring.cloud.gateway.httpclient.ssl.use-insecure-trust-manager=true \
 -Dreactor.netty.http.server.accessLogEnabled=true" \
    TZ=Asia/Kolkata \
    SPRING_PROFILES_ACTIVE=prod \
    APP_HOME=/app \
    LOG_PATH=/var/log/${MODULE_NAME} \
    MODULE_PORT=${MODULE_PORT}

WORKDIR ${APP_HOME}

COPY --from=build /build/backend/${MODULE_NAME}/target/*.jar ./app.jar

RUN mkdir -p /app/config /app/keystore && \
    echo "✅ Config + keystore directories ready"

COPY infra/certs/smartchat.p12 /app/keystore/smartchat.p12
RUN chmod 644 /app/keystore/smartchat.p12 && echo "✅ smartchat.p12 copied"

RUN addgroup -S smartchat && adduser -S smartchat -G smartchat && \
    mkdir -p ${LOG_PATH} && ln -sf ${LOG_PATH} ${APP_HOME}/logs && \
    chown -R smartchat:smartchat ${LOG_PATH} ${APP_HOME}
USER smartchat

EXPOSE ${MODULE_PORT}

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD if [ "$MODULE_NAME" = "smartchat-api" ]; then \
        curl -kfs https://localhost:${MODULE_PORT}/actuator/health | grep '"status":"UP"' || exit 1; \
      else \
        curl -fs http://localhost:${MODULE_PORT}/actuator/health | grep '"status":"UP"' || exit 1; \
      fi

VOLUME ["/var/log/${MODULE_NAME}"]

ENTRYPOINT ["sh", "-c", "echo '✅ Launching SmartChat ${MODULE_NAME} with /app/config/application-prod.yml (merged)...' && java $JAVA_OPTS -Dspring.config.additional-location=file:/app/config/ -Dspring.profiles.active=prod -jar app.jar"]
