###############################################################################
# 🧩 SmartChat Secret Initialization Script (Windows PowerShell)
# Ensures UTF-8 (no BOM) secrets are created before Docker Compose starts
###############################################################################

Write-Host "`n🔐 Initializing SmartChat secrets..." -ForegroundColor Cyan

$base = "C:\Prakhar\app-work\smartChat\secrets"
if (-not (Test-Path $base)) {
    New-Item -ItemType Directory -Path $base | Out-Null
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$secrets = @{
    "postgres_password.txt"      = "StrongPgPassword123!"
    "mongo_root_password.txt"    = "SuperMongoRootPass!"
    "rabbitmq_password.txt"      = "SmartRabbitPass123!"
    "jwt_secret.txt"             = "S0m3Sup3rJWTSecretKey!"
    "grafana_admin_password.txt" = "GrafanaAdmin123!"
}

foreach ($kvp in $secrets.GetEnumerator()) {
    $path = Join-Path $base $kvp.Key
    [System.IO.File]::WriteAllText($path, $kvp.Value, $utf8NoBom)
    Write-Host "✅ Created $path"
}

# Optional cleanup if you want to force a clean database start:
# Write-Host "`n🧹 Removing old Docker volumes (for clean Mongo/Postgres init)..." -ForegroundColor Yellow
# docker compose down -v

Write-Host "`n🚀 Starting Docker Compose..." -ForegroundColor Green
docker compose up -d
