@echo off
echo =======================================
echo ⚠️ FULL CLEANUP: Stopping and removing all data...
echo =======================================

cd /d C:\Prakhar\smartChat

REM Stop containers + remove volumes (DB, Redis, Mongo data will be wiped!)
docker compose down -v

REM Optionally, clear log files
echo Deleting logs...
rmdir /s /q logs

echo =======================================
echo ✅ Cleanup complete!
echo =======================================
pause
