Clear-Host

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "   SMARTCHAT DIAGNOSTICS TOOL (Windows Version)"     -ForegroundColor Green
Write-Host "===================================================" -ForegroundColor Cyan

function Test-Dns {
    param(
        [string]$HostName
    )

    Write-Host "`n[1] DNS Lookup for $HostName" -ForegroundColor Yellow
    try {
        $result = Resolve-DnsName -Name $HostName -ErrorAction Stop
        foreach ($r in $result) {
            Write-Host "Resolved: $($r.IPAddress)" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "DNS Lookup Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Test-Port {
    param(
        [string]$HostName,
        [int]$Port
    )

    Write-Host "`n[2] Port Test: ${HostName}:${Port}" -ForegroundColor Yellow
    try {
        $test = Test-NetConnection -ComputerName $HostName -Port $Port
        if ($test.TcpTestSucceeded) {
            Write-Host "SUCCESS → Port $Port reachable!" -ForegroundColor Green
        } else {
            Write-Host "FAILED → Port $Port NOT reachable" -ForegroundColor Red
        }
    }
    catch {
        Write-Host "Test-NetConnection error: $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Test-Url {
    param(
        [string]$Url
    )

    Write-Host "`n[3] HTTP Request Test: $Url" -ForegroundColor Yellow
    try {
        $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10
        Write-Host "SUCCESS → HTTP $($resp.StatusCode)" -ForegroundColor Green
    }
    catch {
        Write-Host "FAILED → $($_.Exception.Message)" -ForegroundColor Red
    }
}

# ============================================================
# CONFIGURATION (edit if needed)
# ============================================================

$domain      = "app.smartchat.ai"
$callbackUrl = "https://app.smartchat.ai/api/contact/google/callback"

# ============================================================
# Run Tests
# ============================================================

Test-Dns -HostName $domain
Test-Port -HostName $domain -Port 443
Test-Url -Url $callbackUrl

Write-Host "`n===================================================" -ForegroundColor Cyan
Write-Host " Diagnostics Completed " -ForegroundColor Magenta
Write-Host "===================================================" -ForegroundColor Cyan
