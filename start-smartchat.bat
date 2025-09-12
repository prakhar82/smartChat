@echo off
echo =======================================
echo 🚀 Starting SmartChat Docker Stack...
echo =======================================

REM Navigate to project root
cd /d C:\Prakhar\smartChat

REM Build and start stack
docker compose up -d --build

echo =======================================
echo ✅ SmartChat stack started!
echo ---------------------------------------
echo 🌐 Frontend:  http://localhost
echo 📡 API:       http://localhost:%API_PORT%
echo 🛢️ Postgres:  localhost:%POSTGRES_PORT%
echo 🍃 MongoDB:   localhost:%MONGO_PORT%
echo ⚡ Redis:     localhost:%REDIS_PORT%
echo =======================================
pause
