@echo off
REM ============================================================
REM SmartChat Logs Helper (Windows Batch)
REM ------------------------------------------------------------
REM Usage:
REM   logs.bat <service>
REM Examples:
REM   logs.bat api
REM   logs.bat redis
REM   logs.bat web
REM   logs.bat all
REM ============================================================

set SERVICE=%1

if "%SERVICE%"=="" (
  echo ❌ Please provide a service name: postgres ^| mongo ^| redis ^| rabbitmq ^| api ^| web ^| all
  exit /b 1
)

:: =========================
:: PostgreSQL Logs
:: =========================
if /i "%SERVICE%"=="postgres" (
  echo 📘 Tailing PostgreSQL logs...
  docker exec -it pg tail -f /var/log/postgresql/postgres.log
  exit /b
)

if /i "%SERVICE%"=="pg" (
  echo 📘 Tailing PostgreSQL logs...
  docker exec -it pg tail -f /var/log/postgresql/postgres.log
  exit /b
)

:: =========================
:: MongoDB Logs
:: =========================
if /i "%SERVICE%"=="mongo" (
  echo 🍃 Tailing MongoDB logs...
  docker exec -it mongo tail -f /var/log/mongodb/mongod.log
  exit /b
)

:: =========================
:: Redis Logs
:: =========================
if /i "%SERVICE%"=="redis" (
  echo ⚡ Tailing Redis logs...
  REM Default to container logs since Redis logs to stdout
  docker logs -f redis
  REM If file-based logging was configured, uncomment below:
  REM docker exec -it redis tail -f /var/log/redis/redis.log
  exit /b
)

:: =========================
:: RabbitMQ Logs
:: =========================
if /i "%SERVICE%"=="rabbitmq" (
  echo 🐇 Tailing RabbitMQ logs...
  docker exec -it rabbitmq tail -f /var/log/rabbitmq/*.log
  exit /b
)

:: =========================
:: Spring Boot API Logs
:: =========================
if /i "%SERVICE%"=="api" (
  echo 🖥 Tailing Spring Boot API logs...
  if exist ".\logs\api\api.log" (
    powershell -Command "Get-Content -Path '.\logs\api\api.log' -Wait"
  ) else (
    echo ⚠ Local log file not found, showing Docker logs instead...
    docker logs -f api
  )
  exit /b
)

:: =========================
:: Angular Frontend (Nginx) Logs
:: =========================
if /i "%SERVICE%"=="web" (
  echo 🌐 Tailing Web (Nginx) logs...
  if exist ".\logs\web\error.log" (
    echo 📜 Tailing Nginx error.log
    powershell -Command "Get-Content -Path '.\logs\web\error.log' -Wait"
  ) else (
    echo ⚠ Local log file not found, showing Docker logs instead...
    docker logs -f web
  )
  exit /b
)

:: =========================
:: Tail Logs for All Services
:: =========================
if /i "%SERVICE%"=="all" (
  echo 📦 Tailing logs for ALL services...
  docker compose logs -f
  exit /b
)

echo ❌ Unknown service: %SERVICE%
echo Available: postgres ^| mongo ^| redis ^| rabbitmq ^| api ^| web ^| all
exit /b 1
