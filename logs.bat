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
REM ============================================================

set SERVICE=%1

if "%SERVICE%"=="" (
  echo ❌ Please provide a service name: postgres ^| mongo ^| redis ^| api ^| web ^| all
  exit /b 1
)

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

if /i "%SERVICE%"=="mongo" (
  echo 🍃 Tailing MongoDB logs...
  docker exec -it mongo tail -f /var/log/mongodb/mongod.log
  exit /b
)

if /i "%SERVICE%"=="redis" (
  echo ⚡ Tailing Redis logs...
  docker logs -f redis
  REM If you kept file logging instead of stdout:
  REM docker exec -it redis tail -f /var/log/redis/redis.log
  exit /b
)

if /i "%SERVICE%"=="api" (
  echo 🖥 Tailing Spring Boot API logs...
  if exist ".\logs\api\api.log" (
    powershell -Command "Get-Content -Path .\logs\api\api.log -Wait"
  ) else (
    docker logs -f api
  )
  exit /b
)

if /i "%SERVICE%"=="web" (
  echo 🌐 Tailing Web (Nginx) logs...
  if exist ".\logs\web\error.log" (
    powershell -Command "Get-Content -Path .\logs\web\error.log -Wait"
  ) else (
    docker logs -f web
  )
  exit /b
)

if /i "%SERVICE%"=="all" (
  echo 📦 Tailing logs for ALL services...
  docker-compose logs -f
  exit /b
)

echo ❌ Unknown service: %SERVICE%
echo Available: postgres ^| mongo ^| redis ^| api ^| web ^| all
exit /b 1
