<#
.SYNOPSIS
  SmartChat Full Rebuild Utility (Maven + Docker)
.DESCRIPTION
  Cleans and rebuilds SmartChat backend modules, validates security filters,
  and restarts Docker services with health checks.
#>

param(
    [string]$Module = ""
)

# =====================================================================
# Utility Functions
# =====================================================================
function Show-Title {
    param([string]$Text)
    Write-Host ""
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host ">>> $Text" -ForegroundColor Cyan
    Write-Host "======================================================================" -ForegroundColor Cyan
}

function Show-Menu {
    Write-Host ""
    Write-Host "Select a SmartChat module to rebuild:" -ForegroundColor Yellow
    Write-Host "  [1] smartchat-api"
    Write-Host "  [2] smartchat-auth"
    Write-Host "  [3] smartchat-chat"
    Write-Host "  [4] smartchat-contact"
    Write-Host "  [5] smartchat-web"
    Write-Host "  [6] ALL modules"
    Write-Host "  [0] Exit"
    Write-Host ""
    return Read-Host "Enter your choice (0-6)"
}

# =====================================================================
# Module Selection
# =====================================================================
if (-not $Module) {
    $choice = Show-Menu
    switch ($choice) {
        1 { $Module = "smartchat-api" }
        2 { $Module = "smartchat-auth" }
        3 { $Module = "smartchat-chat" }
        4 { $Module = "smartchat-contact" }
        5 { $Module = "smartchat-web" }
        6 { $Module = "all" }
        0 { Write-Host "Exiting..." -ForegroundColor Yellow; exit }
        default { Write-Host "Invalid choice."; exit 1 }
    }
}

# =====================================================================
# Rebuild Process
# =====================================================================
Show-Title "Starting SmartChat Rebuild"
$ErrorActionPreference = "Stop"

# Enable Docker BuildKit
[System.Environment]::SetEnvironmentVariable('DOCKER_BUILDKIT', '1', 'Process')
[System.Environment]::SetEnvironmentVariable('COMPOSE_DOCKER_CLI_BUILD', '1', 'Process')

Write-Host "Stopping existing containers..." -ForegroundColor Yellow
docker compose down -v --remove-orphans | Out-Null

Write-Host "Cleaning Docker and Maven cache..." -ForegroundColor Yellow
docker builder prune -af | Out-Null
docker system prune -af | Out-Null

Write-Host "Removing Maven target folders..." -ForegroundColor Yellow
Get-ChildItem -Path backend -Recurse -Directory -Filter "target" | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "Running Maven clean + package..." -ForegroundColor Cyan
Push-Location backend
if ($Module -eq "all") {
    mvn clean package -DskipTests -U
} else {
    mvn clean package -DskipTests -U -pl $Module,smartchat-common -am
}
Pop-Location

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed during Maven phase." -ForegroundColor Red
    exit 1
}

# =====================================================================
# Security Verification for API Module
# =====================================================================
if ($Module -eq "smartchat-api" -or $Module -eq "all") {
    Write-Host "Verifying SmartChat API security artifacts..." -ForegroundColor Cyan

    $jarFile = Get-ChildItem "backend\smartchat-api\target\" -Filter "*.jar" | Select-Object -First 1
    if (-not $jarFile) {
        Write-Host "ERROR: No JAR found in backend\smartchat-api\target" -ForegroundColor Red
        exit 1
    }

    Write-Host "Found JAR: $($jarFile.Name)" -ForegroundColor Green

    if (-not (Get-Command jar -ErrorAction SilentlyContinue)) {
        Write-Host "ERROR: 'jar' command not found in PATH." -ForegroundColor Red
        exit 1
    }

    $jwtCheck = & jar tf $jarFile.FullName | Select-String "JwtReactiveAuthenticationFilter.class"
    if ($jwtCheck) {
        Write-Host "Verified: JwtReactiveAuthenticationFilter.class found." -ForegroundColor Green
    } else {
        Write-Host "ERROR: JwtReactiveAuthenticationFilter.class missing!" -ForegroundColor Red
        exit 1
    }
}

# =====================================================================
# Docker Build and Restart
# =====================================================================
Write-Host "Building Docker images..." -ForegroundColor Cyan
if ($Module -eq "all") {
    docker compose build --no-cache | Tee-Object -Variable buildOutput
} else {
    docker compose build --no-cache $Module | Tee-Object -Variable buildOutput
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker build failed. Check logs above." -ForegroundColor Red
    exit 1
}

Write-Host "Starting containers..." -ForegroundColor Yellow
docker compose up -d | Out-Null

Start-Sleep -Seconds 10
Write-Host "Checking container status..." -ForegroundColor Cyan
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# =====================================================================
# Wait for Healthy Status
# =====================================================================
Write-Host "Waiting for services to become healthy..." -ForegroundColor Yellow
$startTime = Get-Date
$timeout = 180
$allHealthy = $false

while ((Get-Date) - $startTime -lt (New-TimeSpan -Seconds $timeout)) {
    $statuses = docker ps --format "{{.Names}}:{{.Status}}"
    if ($statuses -match "starting") {
        Write-Host "Services still starting..." -ForegroundColor Yellow
        Start-Sleep -Seconds 5
    } elseif ($statuses -notmatch "unhealthy" -and $statuses -match "healthy") {
        $allHealthy = $true
        break
    } else {
        Start-Sleep -Seconds 5
    }
}

if ($allHealthy) {
    Write-Host ""
    Write-Host "All SmartChat services are UP and HEALTHY!" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "Some containers may still be starting or unhealthy." -ForegroundColor Yellow
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

# =====================================================================
# Final Output
# =====================================================================
Show-Title "SMARTCHAT REBUILD COMPLETE"
Write-Host "Environment refreshed successfully." -ForegroundColor Green
Write-Host ""
Write-Host ("Open SmartChat UI at: http://localhost or https://localhost") -ForegroundColor Cyan
Write-Host ("Check container status with: docker compose ps") -ForegroundColor DarkGray
Write-Host ("View logs using: docker compose logs -f smartchat-api") -ForegroundColor DarkGray
Write-Host ""
