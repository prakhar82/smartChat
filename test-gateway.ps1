$Gateway = "https://localhost:8443"
$Token = "YOUR_JWT_TOKEN_HERE"

Write-Host "=== 🔍 Testing SmartChat Gateway ===" -ForegroundColor Cyan

Write-Host "`n1️⃣ Testing CORS preflight..." -ForegroundColor Yellow
curl.exe -X OPTIONS "$Gateway/api/contacts/matched" `
  -H "Origin: https://app.smartchat.ai" `
  -H "Access-Control-Request-Method: GET" `
  -k -i

Write-Host "`n2️⃣ Testing valid JWT request..." -ForegroundColor Yellow
curl.exe -X GET "$Gateway/api/contacts/matched" `
  -H "Authorization: Bearer $Token" `
  -H "Accept: application/json" `
  -k -i

Write-Host "`n3️⃣ Testing invalid JWT request..." -ForegroundColor Yellow
curl.exe -X GET "$Gateway/api/contacts/matched" `
  -H "Authorization: Bearer invalidtoken" `
  -H "Accept: application/json" `
  -k -i

Write-Host "`n4️⃣ Checking actuator health..." -ForegroundColor Yellow
curl.exe "$Gateway/actuator/health" -k -i

Write-Host "`n✅ All tests executed." -ForegroundColor Green
