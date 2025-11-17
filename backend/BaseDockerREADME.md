###############################################################################

# 🏗️ SMARTCHAT UNIVERSAL BACKEND BUILDER — Production Ready Version

# ---------------------------------------------------------------------------

# ✅ BuildKit optimized: caches Maven dependencies (fast rebuilds)

# ✅ Parallel build threads (mvn -T 1C)

# ✅ Validates security classes from smartchat-common

# ✅ Safe for docker compose --parallel

# ✅ Lightweight runtime image with HTTPS & healthchecks

###############################################################################

FROM maven:3.9.8-eclipse-temurin-17 AS build

LABEL maintainer="SmartChat DevOps Team [team@smartchat.app](mailto:team@smartchat.app)"
org.opencontainers.image.title="SmartChat Backend Module"
org.opencontainers.image.source="[https://github.com/smartchat/backend](https://github.com/smartchat/backend)"
org.opencontainers.image.licenses="Proprietary"

WORKDIR /build

# ---------------------------------------------------------------------------

# ⚙️ Prepare Maven environment

# ---------------------------------------------------------------------------

COPY backend/pom.xml ./backend/pom.xml
RUN mkdir -p /root/.m2 &&
if [ -f backend/settings.xml ]; then
cp backend/settings.xml /root/.m2/settings.xml;
echo "✅ Custom Maven settings.xml copied";
else
echo "ℹ️ No custom settings.xml found, using defaults";
fi
COPY backend ./backend

# ---------------------------------------------------------------------------

# 🔨 Build module with Maven caching & parallel threads

# ---------------------------------------------------------------------------

ARG MODULE_NAME
RUN --mount=type=cache,target=/root/.m2
--mount=type=cache,target=/root/.cache/mvn
echo "🔨 Building SmartChat module: ${MODULE_NAME}" &&
cd backend &&
MAVEN_OPTS='-Xmx1g -Duser.home=/root' mvn -B -T 1C clean package -DskipTests
-pl ${MODULE_NAME},smartchat-common -am &&
echo "✅ Build completed for ${MODULE_NAME}" && ls -lh ${MODULE_NAME}/target/

# ---------------------------------------------------------------------------

# 🔐 Security verification: ensure key reactive security classes exist

# ---------------------------------------------------------------------------

RUN echo "🔍 Verifying security artifacts for ${MODULE_NAME}..." &&
if [ "${MODULE_NAME}" != "smartchat-discovery" ] && [ "${MODULE_NAME}" != "smartchat-builder" ]; then
if jar tf backend/${MODULE_NAME}/target/*.jar | grep -q "smartchat-common"; then
mkdir -p backend/${MODULE_NAME}/target/BOOT-INF/lib;
( jar tf backend/${MODULE_NAME}/target/*.jar | grep -q "ReactiveSecurityConfigBase.class" ||
(jar xf backend/${MODULE_NAME}/target/*.jar BOOT-INF/lib || true &&
find backend/${MODULE_NAME}/target/BOOT-INF/lib -type f -name "*.jar" -exec jar tf {} ; |
grep -q "ReactiveSecurityConfigBase.class" ||
(echo "❌ Missing ReactiveSecurityConfigBase.class" && exit 1)) );
( jar tf backend/${MODULE_NAME}/target/*.jar | grep -q "JwtReactiveAuthenticationFilter.class" ||
(find backend/${MODULE_NAME}/target/BOOT-INF/lib -type f -name "*.jar" -exec jar tf {} ; |
grep -q "JwtReactiveAuthenticationFilter.class" ||
(echo "❌ Missing JwtReactiveAuthenticationFilter.class" && exit 1)) );
echo "✅ Security verification passed for ${MODULE_NAME}";
else
echo "ℹ️ ${MODULE_NAME} does not bundle smartchat-common — skipping check.";
fi;
else
echo "ℹ️ Skipping verification for ${MODULE_NAME} (non-secured module).";
fi

###############################################################################

# 🚀 STAGE 2 — Lightweight Runtime Image

###############################################################################
FROM eclipse-temurin:17-jre-alpine AS runtime

USER root

RUN apk add --no-cache curl grep tzdata &&
ln -snf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime &&
echo "Asia/Kolkata" > /etc/timezone

ARG MODULE_NAME
ARG MODULE_PORT=8080

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
TZ=Asia/Kolkata
SPRING_PROFILES_ACTIVE=prod
APP_HOME=/app
LOG_PATH=/var/log/${MODULE_NAME}
MODULE_PORT=${MODULE_PORT}

WORKDIR ${APP_HOME}

# ✅ Copy JAR from build stage

COPY --from=build /build/backend/${MODULE_NAME}/target/*.jar ./app.jar

# 🔒 Copy SSL keystore (for smartchat-api)

RUN mkdir -p /app/keystore &&
if [ -f infra/certs/smartchat.p12 ]; then
cp infra/certs/smartchat.p12 /app/keystore/smartchat.p12;
echo "✅ smartchat.p12 copied to runtime keystore.";
else
echo "⚠️ smartchat.p12 not found — HTTPS may not start.";
fi

# 👤 Non-root user setup

RUN addgroup -S smartchat && adduser -S smartchat -G smartchat &&
mkdir -p ${LOG_PATH} && ln -sf ${LOG_PATH} ${APP_HOME}/logs &&
chown -R smartchat:smartchat ${LOG_PATH} ${APP_HOME}

USER smartchat

EXPOSE ${MODULE_PORT}

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3
CMD if [ "$MODULE_NAME" = "smartchat-api" ]; then
curl -kfs [https://localhost:${MODULE_PORT}/actuator/health](https://localhost:${MODULE_PORT}/actuator/health) | grep '"
status":"UP"' || exit 1;
else
curl -fs [http://localhost:${MODULE_PORT}/actuator/health](http://localhost:${MODULE_PORT}/actuator/health) | grep '"
status":"UP"' || exit 1;
fi

VOLUME ["/var/log/${MODULE_NAME}"]

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

###############################################################################

# 🧪 Automated Metrics Validation (Prometheus + Grafana)

###############################################################################

# Optional post-deployment validation script (verify.sh will check these)

# - Verifies Prometheus metrics endpoint

# - Confirms Grafana dashboard availability

###############################################################################

# (Included in CI/CD verify.sh script)

````

---

## 🧠 Extended verify.sh Add-on (Prometheus + Grafana Checks)

Append this block to your existing `scripts/verify.sh` file:

```bash
# ---------------------------------------------------------------------------
# 8. Prometheus & Grafana validation
# ---------------------------------------------------------------------------
PROM_URL="http://localhost:9090/api/v1/status/runtimeinfo"
GRAFANA_URL="http://localhost:3000/api/health"

log_warn "Checking Prometheus availability..."
if curl -fs ${PROM_URL} | grep 'status'; then
  log_success "Prometheus is responding."
else
  log_error "Prometheus is not reachable."
fi

log_warn "Checking Grafana health..."
if curl -fs ${GRAFANA_URL} | grep 'database'; then
  log_success "Grafana API is reachable."
else
  log_error "Grafana not responding — check port 3000 or credentials."
fi
````

---

✅ **This is now the final production-ready Dockerfile and verification toolkit.**
It’s compatible with your full SmartChat stack (`docker compose up -d`) and supports full CI/CD validation including
Prometheus and Grafana metrics health checks.
